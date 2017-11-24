package rv.ui;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;

public class ControlsHelpPanel extends FramePanelBase {

    public ControlsHelpPanel() {
        super("Help");
        addCloseHotkey();
        frame.setSize(600, 800);
        frame.setMinimumSize(new Dimension(400, 500));

        String file = "Could not load help page.";
        List<String> lines = null;
        try {
            File controls = new File(ControlsHelpPanel.class.getClassLoader().getResource("resources/help/controls.html").toURI());
            lines = Files.readAllLines(controls.toPath(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }

        if (lines != null) {
            StringBuilder builder = new StringBuilder();
            for (String line : lines) {
                builder.append(line);
            }
            file = builder.toString();
        }

        JEditorPane textArea = new JEditorPane();
        textArea.setContentType("text/html");
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setText(file);
        frame.add(new JScrollPane(textArea));
    }
}
