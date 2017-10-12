package rv;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLProfile;
import javax.swing.JFrame;
import js.jogl.prog.GLProgram;
import js.jogl.view.Viewport;
import rv.comm.NetworkManager;
import rv.comm.drawing.Drawings;
import rv.comm.rcssserver.LogPlayer;
import rv.ui.UserInterface;
import rv.ui.menus.MenuBar;
import rv.world.WorldModel;

/**
 *
 * @author Philipp Strobel <philippstrobel@posteo.de>
 */
abstract public class Viewer extends GLProgram
{
    protected WorldModel                       world;
    protected UserInterface                    ui;
    protected NetworkManager                   netManager;
    protected Drawings                         drawings;
    protected Renderer                         renderer;
    protected LogPlayer                        logPlayer;
    protected final Configuration              config;
    protected Mode                             mode                  = Mode.LIVE;

    protected final List<WindowResizeListener> windowResizeListeners = new ArrayList<>();

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
    
    public WorldModel getWorldModel() {
        return world;
    }

    public Mode getMode() {
        return mode;
    }
    
    public Configuration getConfig() {
        return config;
    }
    
    public UserInterface getUI() {
        return ui;
    }

    public Drawings getDrawings() {
        return drawings;
    }

    public NetworkManager getNetManager() {
        return netManager;
    }

    public LogPlayer getLogPlayer() {
        return logPlayer;
    }

    public Renderer getRenderer() {
        return renderer;
    }

    public void addWindowResizeListener(WindowResizeListener l) {
        windowResizeListeners.add(l);
    }

    public void removeWindowResizeListener(WindowResizeListener l) {
        windowResizeListeners.remove(l);
    }

    abstract public JFrame getFrame();
    abstract public MenuBar getMenu();
    abstract public void toggleFullScreen();
    abstract public void takeScreenShot();
}
