package rv;

import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.awt.AWTKeyAdapter;
import com.jogamp.newt.event.awt.AWTMouseAdapter;
import com.jogamp.opengl.util.awt.Screenshot;
import java.awt.BorderLayout;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import javax.swing.JPanel;
import js.jogl.GLInfo;
import rv.comm.NetworkManager;
import rv.comm.drawing.Drawings;
import rv.comm.rcssserver.LogPlayer;
import rv.comm.rcssserver.ServerComm;
import rv.comm.rcssserver.scenegraph.SceneGraph;
import rv.content.ContentManager;
import rv.ui.UserInterface;
import rv.ui.menus.MenuBar;
import rv.world.WorldModel;

/**
 *
 * @author Philipp Strobel <philippstrobel@posteo.de>
 */
public class ViewerPanel extends Viewer
        implements GLEventListener, ServerComm.ServerChangeListener, LogPlayer.StateChangeListener
{
    private static final String VERSION = "1.3.0";
    
    private JFrame   parent;
    private JPanel   panel;
    private GLCanvas canvas;
    private MenuBar  menuBar;
    private boolean  movedFrame;
    private boolean  init = false;
    private GLInfo   glInfo;
    private ContentManager contentManager;
    private String drawingFilter;
    private File logFile;
    private String ssName = null;
    
    public ViewerPanel() {
        this(Configuration.loadFromFile());
    }

    public ViewerPanel(Configuration config) {
        super(config, config.graphics.frameWidth, config.graphics.frameHeight);
        
        final GLCapabilities caps = determineGLCapabilities(config);

        initComponents(caps);
        
        System.out.println("RoboViz " + VERSION + "\n");
    }
    
    private void initComponents(GLCapabilities caps) {
        canvas = new GLCanvas(caps);
        canvas.setFocusTraversalKeysEnabled(false);

        panel = new JPanel();
        
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                movedFrame = true;
            }
        });
        
        panel.setLayout(new BorderLayout());
        panel.add(canvas, BorderLayout.CENTER);
        attachDrawableAndStart(canvas);
        
        menuBar = new MenuBar(this);
        // TODO: do something with the menubar ?!
    }
    
    public JPanel getPanel() {
        return panel;
    }
    
    public void setFrame(JFrame f) {
        parent = f;
    }
    
    @Override
    public JFrame getFrame() {
        return parent;
    }

    @Override
    public MenuBar getMenu() {
        return menuBar;
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
    
    public void exitError(String msg) {
        System.err.println(msg);
        if (animator != null)
            animator.stop();
        // TODO: show something usefull in the panel?!
    }

    @Override
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
    public void render(GL gl) {
        if (!init)
            return;

        if (ssName != null) {
            takeScreenshot(ssName);
            ssName = null;
        }

        renderer.render(drawable, config.graphics);
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
    public void addKeyListener(KeyListener l) {
        (new AWTKeyAdapter(l)).addTo(canvas);
    }

    @Override
    public void addMouseListener(MouseListener l) {
        (new AWTMouseAdapter(l)).addTo(canvas);
    }

    @Override
    public void connectionChanged(ServerComm server) {
        // TODO: is this usefull in a JPanel
    }

    @Override
    public void playerStateChanged(boolean playing) {
        if (getUI().getBallTracker() != null)
            getUI().getBallTracker().setPlaybackSpeed(logPlayer.getPlayBackSpeed());
    }

    @Override
    public void logfileChanged() {
        // TODO: is this usefull in a JPanel
    }

    @Override
    public void toggleFullScreen() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void takeScreenShot() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
