package rv;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Helper class for loading the ViewerPanel.
 * 
 * @author Philipp Strobel <philippstrobel@posteo.de>
 */
public class ViewerPanelLoader
{
    /**
     * Loads the needed libraries for the ViewerPanel and returns an instance
     * of the ViewerPanel as an Object.
     * 
     * We must return an 'Object', because if we return the ViewerPanel itself
     * java would try to evaluate the return value before the necessary libraries
     * are loaded!
     * 
     * @return a new instance of the ViewerPanel
     */
    public static Object load() {
        setLibraryPaths();
        return new ViewerPanel();
    }

    /**
     * Loads the needed libraries for the ViewerPanel and returns an instance
     * of the ViewerPanel with the given configuration as an Object.
     * 
     * We must return an 'Object', because if we return the ViewerPanel itself
     * java would try to evaluate the return value before the necessary libraries
     * are loaded!
     * 
     * @param config the Configuration which should be used
     * @return a new instance of the ViewerPanel
     */
    public static Object load(Configuration config) {
        setLibraryPaths();
        return new ViewerPanel(config);
    }
    
    /**
     * Sets/Adds the necessary library search paths for the ViewerPanel.
     * Therefore the location of the jar file is determined and the containing
     * directory is used for the search path of the necessary libraries.
     */
    public static void setLibraryPaths() {
        try {
            // get the location of the roboviz jar file
            File jar = new File(ViewerPanelLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // the jar directory is already in the search path!
            if(Arrays.asList(((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs()).contains(jar.getParentFile().toURI().toURL())) {
                return;
            }
            // assuming that the lib directory is next to the roboviz jar file
            File lib = new File(jar.getParent() + "/lib");
            if(lib.exists() && lib.isDirectory()) {
                // add the jar directory to the search path (the default config.txt should be there)
                addPath(jar.getParentFile());
                // add the lib directory to the system path (for loading native libs)
                addLibraryPath(lib.getAbsolutePath());
                // add the lib directoy itself to the search path
                addPath(lib);
                // iterate through the files of the lib directory and add all jar files to the search path
                File[] files = lib.listFiles();
                for(File f : files) {
                    if(f.isFile() && f.getName().endsWith(".jar")) {
                        addPath(f);
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(ViewerPanelLoader.class.getName()).log(Level.SEVERE, "An error occurred while loading libraries! RoboViz might not work as aspected.", ex);
        }
    }
    
    /**
     * Appends the specified URL to the list of URLs to search for classes and resources.
     * 
     * @param f the File which should be added to the search path.
     * @throws Exception 
     */
    private static void addPath(File f) throws Exception {
        URI u = f.toURI();
        URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<URLClassLoader> urlClass = URLClassLoader.class;
        Method method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
        method.setAccessible(true);
        method.invoke(urlClassLoader, new Object[]{u.toURL()});
    }
    
    /**
     * Adds the specified path to the java library path
     *
     * @param pathToAdd the path to add
     * @throws Exception
     */
    private static void addLibraryPath(String pathToAdd) throws Exception {
        final Field usrPathsField = ClassLoader.class.getDeclaredField("usr_paths");
        usrPathsField.setAccessible(true);

        //get array of paths
        final String[] paths = (String[]) usrPathsField.get(null);

        //check if the path to add is already present
        for (String path : paths) {
            if (path.equals(pathToAdd)) {
                return;
            }
        }

        //add the new path
        final String[] newPaths = Arrays.copyOf(paths, paths.length + 1);
        newPaths[newPaths.length - 1] = pathToAdd;
        usrPathsField.set(null, newPaths);
    }
}
