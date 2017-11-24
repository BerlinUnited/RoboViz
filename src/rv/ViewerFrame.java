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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.PrintStream;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.awt.GLCanvas;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import rv.comm.rcssserver.LogPlayer;
import rv.comm.rcssserver.ServerComm;
import rv.ui.menus.MenuBar;
import rv.util.commandline.Argument;
import rv.util.commandline.BooleanArgument;
import rv.util.commandline.IntegerArgument;
import rv.util.commandline.StringArgument;

/**
 * Program entry point / main class. Creates a window and delegates OpenGL rendering the Renderer
 * object.
 * 
 * @author Justin Stoecker
 */
public class ViewerFrame extends Viewer
        implements GLEventListener, ServerComm.ServerChangeListener, LogPlayer.StateChangeListener {

    private static final String VERSION = "1.3.0";

    private RVFrame                          frame;
    private boolean                          movedFrame;

    public static void main(String[] args) {
        final Configuration config = Configuration.loadFromFile();

        final GLCapabilities caps = determineGLCapabilities(config);

        final String[] arguments = args;
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new ViewerFrame(config, caps, arguments);
            }
        });
    }

    public ViewerFrame(Configuration config, GLCapabilities caps, String[] args) {
        super(config, config.graphics.frameWidth, config.graphics.frameHeight);

        parseArgs(args);
        initComponents(caps);

        System.out.println("RoboViz " + VERSION + "\n");
    }

    private void parseArgs(String[] args) {
        StringArgument LOG_FILE = new StringArgument("logFile", null);
        BooleanArgument LOG_MODE = new BooleanArgument("logMode");
        StringArgument SERVER_HOST = new StringArgument("serverHost", null);
        IntegerArgument SERVER_PORT = new IntegerArgument("serverPort", null, 1, 65535);
        StringArgument DRAWING_FILTER = new StringArgument("drawingFilter", ".*");

        handleLogModeArgs(LOG_FILE.parse(args), LOG_MODE.parse(args));
        config.networking.overrideServerHost(SERVER_HOST.parse(args));
        config.networking.overrideServerPort(SERVER_PORT.parse(args));
        drawingFilter = DRAWING_FILTER.parse(args);
        Argument.endParse(args);
    }

    private void handleLogModeArgs(String logFilePath, boolean logMode) {
        String error = null;

        if (logFilePath != null) {
            // handle linux home directory
            logFilePath = logFilePath.replaceFirst("^~", System.getProperty("user.home"));

            logFile = new File(logFilePath);
            mode = Mode.LOGFILE;
            if (!logFile.exists())
                error = "Could not find logfile '" + logFilePath + "'";
            else if (logFile.isDirectory())
                error = "The specified logfile '" + logFilePath + "' is a directory";
        }

        if (error != null) {
            System.err.println(error);
            logFile = null;
        }

        if (logMode)
            mode = Mode.LOGFILE;
    }

    private void initComponents(GLCapabilities caps) {
        canvas = new GLCanvas(caps);
        canvas.setFocusTraversalKeysEnabled(false);

        frame = new RVFrame(getTitle(null));
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
        frame.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                movedFrame = true;
            }
        });
        frame.setIconImage(Globals.getIcon());
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        restoreConfig();
        frame.setVisible(true);
        attachDrawableAndStart(canvas);
    }

    private void restoreConfig() {
        Configuration.Graphics graphics = config.graphics;
        Integer frameX = graphics.frameX;
        Integer frameY = graphics.frameY;
        boolean maximized = graphics.isMaximized;

        frame.setSize(graphics.frameWidth, graphics.frameHeight);

        if (graphics.centerFrame)
            frame.setLocationRelativeTo(null);
        else
            frame.setLocation(frameX, frameY);

        frame.setState(maximized ? Frame.MAXIMIZED_BOTH : Frame.NORMAL);
    }

    public void shutdown() {
        if (config.graphics.saveFrameState)
            storeConfig();
        frame.dispose();
        System.exit(0);
    }

    private void storeConfig() {
        Configuration.Graphics graphics = config.graphics;
        Point location = frame.getLocation();
        Dimension size = frame.getSize();
        int state = frame.getState();

        if (movedFrame) {
            graphics.frameX = location.x;
            graphics.frameY = location.y;
            graphics.centerFrame = false;
        }
        graphics.frameWidth = size.width;
        graphics.frameHeight = size.height;
        graphics.isMaximized = (state & Frame.MAXIMIZED_BOTH) > 0;

        config.write();
    }

    @Override
    public void exitError(String msg) {
        System.err.println(msg);
        if (animator != null)
            animator.stop();
        if (frame != null)
            frame.dispose();
        System.exit(1);
    }

    @Override
    public void connectionChanged(ServerComm server) {
        if (mode != Mode.LIVE)
            return;

        String host = server.isConnected() ? config.networking.getServerHost() : null;
        frame.setTitle(getTitle(host));
    }

    @Override
    public void logfileChanged() {
        frame.setTitle(getTitle(logPlayer.getFilePath()));
    }

    private String getTitle(String current) {
        String roboviz = "RoboViz " + VERSION;
        if (current == null)
            return roboviz;
        return current + " - " + roboviz;
    }

    @Override
    public JFrame getFrame() {
        return this.frame;
    }

    @Override
    public MenuBar getMenu() {
        return this.frame.getMenu();
    }

    public class RVFrame extends JFrame {

        private MenuBar menuBar;

        public RVFrame(String title) throws HeadlessException {
            super(title);
            menuBar = new MenuBar(ViewerFrame.this);
            setJMenuBar(menuBar);
        }

        @Override
        public void list(PrintStream out, int indent) {
            // hack to suppress the output of java.awt.Window's
            // hardcoded debugging hotkey Ctrl+Shift+F1
        }

        public MenuBar getMenu() {
            return menuBar;
        }
    }
}
