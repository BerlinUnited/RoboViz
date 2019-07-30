/*
 *  Copyright 2011 RoboViz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rv;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EventObject;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.awt.AWTKeyAdapter;
import com.jogamp.newt.event.awt.AWTMouseAdapter;
import com.jogamp.opengl.util.awt.Screenshot;
import js.jogl.GLInfo;
import js.jogl.prog.GLProgram;
import js.jogl.view.Viewport;
import rv.comm.NetworkManager;
import rv.comm.drawing.Drawings;
import rv.comm.rcssserver.LogPlayer;
import rv.comm.rcssserver.ServerComm;
import rv.comm.rcssserver.scenegraph.SceneGraph;
import rv.content.ContentManager;
import rv.ui.UserInterface;
import rv.ui.menus.MenuBar;
import rv.util.swing.SwingUtil;
import rv.world.WorldModel;

/**
 *
 * @author Philipp Strobel <philippstrobel@posteo.de>
 */
abstract public class Viewer extends GLProgram
        implements GLEventListener, ServerComm.ServerChangeListener, LogPlayer.StateChangeListener {

    private static final String VERSION = "1.6.1";

    public enum Mode {
        LOGFILE, LIVE,
    }

    /** Event object for when the main RoboViz window is resized */
    public class WindowResizeEvent extends EventObject {

        private final Viewport       window;
        private final GLAutoDrawable drawable;

        public Viewport getWindow() {
            return window;
        }

        public GLAutoDrawable getDrawable() {
            return drawable;
        }

        public WindowResizeEvent(Object src, Viewport window) {
            super(src);
            this.window = window;
            this.drawable = getCanvas();
        }
    }

    /** Event listener interface when the main RoboViz window is resized */
    public interface WindowResizeListener {
        void windowResized(WindowResizeEvent event);
    }

    protected final List<WindowResizeListener> windowResizeListeners = new ArrayList<>();

    protected GLCanvas                         canvas;
    protected WorldModel                       world;
    protected UserInterface                    ui;
    protected NetworkManager                   netManager;
    protected ContentManager                   contentManager;
    protected Drawings                         drawings;
    protected Renderer                         renderer;
    protected LogPlayer                        logPlayer;
    protected boolean                          init                  = false;
    protected boolean                          fullscreen            = false;
    protected GLInfo                           glInfo;
    protected final Configuration              config;
    protected String                           ssName                = null;
    protected File                             logFile;
    protected String                           drawingFilter;
    protected Mode                             mode                  = Mode.LIVE;

    public LogPlayer getLogPlayer() {
        return logPlayer;
    }

    public Mode getMode() {
        return mode;
    }

    public GLInfo getGLInfo() {
        return glInfo;
    }

    public Configuration getConfig() {
        return config;
    }

    public Viewport getScreen() {
        return screen;
    }

    public WorldModel getWorldModel() {
        return world;
    }

    public UserInterface getUI() {
        return ui;
    }

    public NetworkManager getNetManager() {
        return netManager;
    }

    public Drawings getDrawings() {
        return drawings;
    }

    abstract public JFrame getFrame();

    public void addWindowResizeListener(WindowResizeListener l) {
        windowResizeListeners.add(l);
    }

    public void removeWindowResizeListener(WindowResizeListener l) {
        windowResizeListeners.remove(l);
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public Viewer(Configuration config, int w, int h) {
        super(w, h);
        this.config = config;
    }
    
    public static GLCapabilities determineGLCapabilities(Configuration config) {
        GLProfile glp = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(glp);
        caps.setStereo(config.graphics.useStereo);
        if (config.graphics.useFsaa) {
            caps.setSampleBuffers(true);
            caps.setNumSamples(config.graphics.fsaaSamples);
        }
        return caps;
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        if (!init) { // print OpenGL renderer info
            glInfo = new GLInfo(drawable.getGL());
            glInfo.print();
        }

        // initialize / load content
        contentManager = new ContentManager(config.teamColors);
        if (!contentManager.init(drawable, glInfo)) {
            exitError("Problems loading resource files!");
        }

        SceneGraph oldSceneGraph = null;
        if (init)
            oldSceneGraph = world.getSceneGraph();
        world = new WorldModel();
        world.init(drawable.getGL(), contentManager, config, mode);
        drawings = new Drawings();
        ui = new UserInterface(this, drawingFilter);

        if (mode == Mode.LIVE) {
            netManager = new NetworkManager();
            netManager.init(this, config);
            netManager.getServer().addChangeListener(world.getGameState());
            netManager.getServer().addChangeListener(this);
        } else {
            if (!init) {
                logPlayer = new LogPlayer(logFile, world, config, this);
                logPlayer.addListener(this);
                logfileChanged();
            } else
                logPlayer.setWorldModel(world);
        }

        ui.init();
        renderer = new Renderer(this);
        renderer.init(drawable, contentManager, glInfo);

        if (init && oldSceneGraph != null)
            world.setSceneGraph(oldSceneGraph);
        world.addSceneGraphListener(contentManager);

        gl.glClearColor(0, 0, 0, 1);
        init = true;
    }

    public void addKeyListener(KeyListener l) {
        (new AWTKeyAdapter(l)).addTo(canvas);
    }

    public void addMouseListener(MouseListener l) {
        (new AWTMouseAdapter(l)).addTo(canvas);
    }

    public void takeScreenShot() {
        String s = Calendar.getInstance().getTime().toString();
        s = s.replaceAll("[\\s:]+", "_");
        ssName = String.format(Locale.US, "screenshots/%s_%s.png", "roboviz", s);
    }

    private void takeScreenshot(String fileName) {
        BufferedImage ss = Screenshot.readToBufferedImage(0, 0, screen.w, screen.h, false);
        File ssFile = new File(fileName);
        File ssDir = new File("screenshots");
        try {
            if (!ssDir.exists())
                ssDir.mkdir();
            ImageIO.write(ss, "png", ssFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Screenshot taken: " + ssFile.getAbsolutePath());
    }

    /** Enter or exit full-screen exclusive mode depending on current mode */
    public void toggleFullScreen() {
        fullscreen = !fullscreen;
        SwingUtil.getCurrentScreen(getFrame()).setFullScreenWindow(fullscreen ? getFrame() : null);
    }

    abstract public void exitError(String msg);

    public void update(GL glGeneric) {
        if (!init)
            return;

        GL2 gl = glGeneric.getGL2();
        contentManager.update(gl);
        ui.update(gl, elapsedMS);
        world.update(gl, elapsedMS, ui);
        drawings.update();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();

        if (netManager != null)
            netManager.shutdown();
        if (world != null)
            world.dispose(gl);
        if (renderer != null)
            renderer.dispose(gl);
        if (contentManager != null)
            contentManager.dispose(gl);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int w, int h) {
        super.reshape(drawable, x, y, w, h);

        WindowResizeEvent event = new WindowResizeEvent(this, screen);
        for (WindowResizeListener l : windowResizeListeners)
            l.windowResized(event);
    }

    @Override
    public void render(GL gl) {
        if (!init)
            return;

        if (ssName != null) {
            takeScreenshot(ssName);
            ssName = null;
        }

        renderer.render(drawable, config.graphics);
    }

    @Override
    public void playerStateChanged(boolean playing) {
        if (getUI().getBallTracker() != null)
            getUI().getBallTracker().setPlaybackSpeed(logPlayer.getPlayBackSpeed());
    }

    abstract public MenuBar getMenu();
