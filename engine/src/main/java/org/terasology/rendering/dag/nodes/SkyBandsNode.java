/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.rendering.dag.nodes;

import org.terasology.config.RenderingConfig;
import org.terasology.monitoring.PerformanceMonitor;
import org.terasology.registry.In;
import org.terasology.rendering.assets.material.Material;
import org.terasology.rendering.cameras.Camera;
import org.terasology.rendering.dag.WireframeCapableNode;
import org.terasology.rendering.opengl.FBO;
import org.terasology.rendering.opengl.FBOConfig;
import org.terasology.rendering.opengl.FrameBuffersManager;
import org.terasology.rendering.world.WorldRenderer;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;
import static org.terasology.rendering.opengl.OpenGLUtils.bindDisplay;
import static org.terasology.rendering.opengl.OpenGLUtils.setViewportToSizeOf;
import static org.terasology.rendering.opengl.OpenGLUtils.renderFullscreenQuad;
import static org.terasology.rendering.opengl.OpenGLUtils.setRenderBufferMask;

/**
 * TODO: Diagram of this node
 * TODO: Separate this node into multiple SkyBandNode's
 */
public class SkyBandsNode extends WireframeCapableNode {
    private static final String SCENE_OPAQUE_FBO = "sceneOpaque";
    @In
    private WorldRenderer worldRenderer;

    @In
    private FrameBuffersManager frameBuffersManager;

    private RenderingConfig renderingConfig;
    private Material blurShader;
    private FBO sceneOpaque;
    private FBO sceneSkyBand0;
    private Camera playerCamera;

    @Override
    public void initialise() {
        super.initialise();
        requireFBO(new FBOConfig(SCENE_OPAQUE_FBO, 1.0f, FBO.Type.HDR).useDepthBuffer().useNormalBuffer().useLightBuffer().useStencilBuffer());
        requireFBO(new FBOConfig("sceneSkyBand0", 0.0625f, FBO.Type.DEFAULT));
        requireFBO(new FBOConfig("sceneSkyBand1", 0.03125f, FBO.Type.DEFAULT));

        renderingConfig = config.getRendering();
        blurShader = worldRenderer.getMaterial("engine:prog.blur");
        playerCamera = worldRenderer.getActiveCamera();
    }

    @Override
    public void process() {
        PerformanceMonitor.startActivity("rendering/skyBands");

        sceneOpaque = frameBuffersManager.getFBO("sceneOpaque");

        setRenderBufferMask(sceneOpaque, true, true, true);
        if (renderingConfig.isInscattering()) {
            sceneSkyBand0 = frameBuffersManager.getFBO("sceneSkyBand0");
            FBO sceneSkyBand1 = frameBuffersManager.getFBO("sceneSkyBand1");

            generateSkyBand(sceneSkyBand0);
            generateSkyBand(sceneSkyBand1);
        }

        sceneOpaque.bind();

        playerCamera.lookThrough();

        PerformanceMonitor.endActivity();
    }

    private void generateSkyBand(FBO skyBand) {
        blurShader.enable();
        blurShader.setFloat("radius", 8.0f, true);
        blurShader.setFloat2("texelSize", 1.0f / skyBand.width(), 1.0f / skyBand.height(), true);

        if (skyBand == sceneSkyBand0) {
            sceneOpaque.bindTexture();
        } else {
            sceneSkyBand0.bindTexture();
        }

        skyBand.bind();
        setRenderBufferMask(skyBand, true, false, false);

        setViewportToSizeOf(skyBand);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // TODO: verify this is necessary

        renderFullscreenQuad();

        bindDisplay();     // TODO: verify this is necessary
        setViewportToSizeOf(sceneOpaque);    // TODO: verify this is necessary
    }
}
