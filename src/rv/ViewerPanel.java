package rv;

import java.awt.BorderLayout;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import rv.comm.rcssserver.ServerComm;
import rv.ui.menus.MenuBar;

/**
 *
 * @author Philipp Strobel <philippstrobel@posteo.de>
 */
public class ViewerPanel extends Viewer
        implements GLEventListener
{
    private static final String VERSION = "1.6.1";
    
    private JFrame          parent;
    private JPanel          panel;
    private MenuBar         menuBar;
    
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
        
        panel.setLayout(new BorderLayout());
        panel.add(canvas, BorderLayout.CENTER);
        attachDrawableAndStart(canvas);
        
        menuBar = new MenuBar(this);
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
    
    public void showMenuBar(boolean show) {
        if(show) {
            panel.add(menuBar, BorderLayout.NORTH);
        } else {
            panel.remove(menuBar);
        }
    }
    
    @Override
    public void exitError(String msg) {
        System.err.println(msg);
        if (animator != null)
            animator.stop();
        panel.remove(canvas);
        panel.add(new JLabel("<html><h2>An error occurred!</h2><br><font color='red'>"+msg+"</font></html>"), BorderLayout.CENTER);
    }

    @Override
    public void connectionChanged(ServerComm server) {
        // NOTE: currently nothing useful to show in a JPanel ...
    }

    @Override
    public void logfileChanged() {
        // NOTE: currently nothing useful to show in a JPanel ...
    }
}