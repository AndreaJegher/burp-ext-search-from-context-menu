package burp;

import java.io.PrintWriter;
import java.util.*;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Desktop;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTextField;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.awt.Component;
import java.awt.event.*;  

public class BurpExtender implements IBurpExtender, IContextMenuFactory, ITab
{
    private static final String SEARCH_URL = "searchURL";
    private static final String BROWSER = "browser";

    private PrintWriter stdout;
    private PrintWriter stderr;

    private String selectedText;
    private byte invocationContext;

    private IBurpExtenderCallbacks callbacks;

    private String browser = "firefox";
    private String searchURL = "https://duckduckgo.com/?q=";
    private JTextField browserInputField;
    private JTextField urlInputField;
    private JPanel panel = new JPanel();
    private JPanel browserFrame = new JPanel();
    private JPanel urlFrame = new JPanel();
    private JButton changeBrowser = new JButton("Change browser");
    private JButton changeURL = new JButton("Change URL");

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks c)
    {
        callbacks = c;
        
        callbacks.setExtensionName("Search");
        
        String b = callbacks.loadExtensionSetting(BROWSER);
        if (b != null) {
            browser = b;
        }
        browserInputField = new JTextField(browser, 30);

        String s = callbacks.loadExtensionSetting(SEARCH_URL);
        if (s != null) {
            searchURL = s;
        }
        urlInputField = new JTextField(searchURL, 50);

        stdout = new PrintWriter(callbacks.getStdout(), true);
        stderr = new PrintWriter(callbacks.getStderr(), true);

        callbacks.registerContextMenuFactory(this);
        callbacks.addSuiteTab(this);
    }
    
    @Override
    public List<JMenuItem> createMenuItems(IContextMenuInvocation invocation)
    {
        ArrayList<JMenuItem> menutItems = new ArrayList<>();

        JMenuItem searchText = new JMenuItem("Search");
        searchText.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchText(selectedText);
            }
        });

        invocationContext = invocation.getInvocationContext();
        int[] bounds = invocation.getSelectionBounds();

        if (bounds.length >= 2) {
            for (IHttpRequestResponse message : invocation.getSelectedMessages()) {
                byte[] src;

                switch(invocationContext){
                    case 0:
                    case 2:
                        src = message.getRequest();
                        break;
                    case 3:
                        src = message.getResponse();
                        break;
                    default:
                        stdout.println("Unhandled context: "+invocationContext);
                        return menutItems;
                }

                if (bounds[1] < src.length) {
                    selectedText = new String (Arrays.copyOfRange(src,bounds[0],bounds[1]));
                }
            }
        }

        menutItems.add(searchText);
        return menutItems;
    }

    public void searchText(String text) {
        String searchURI;
        try {
            searchURI = searchURL+URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
        } catch (Exception ex1) {
            stderr.println(ex1.toString());
            throw new RuntimeException(ex1.getCause());
        }
        try {
            Desktop.getDesktop().browse(new URI(searchURI));
        } catch (Exception ex1)  {
            stderr.println(ex1.toString());
            try {
                ProcessBuilder pb = new ProcessBuilder(browser, searchURI);
                pb.start();
            } catch (Exception ex2) {
                stderr.println(ex2.toString());
                throw new RuntimeException(ex2.getCause());
            }
        }
    }

    @Override
    public String getTabCaption() {
        return "Search";
    }

    @Override
    public Component getUiComponent() {
        panel.setLayout(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Settings Panel"));         
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(10, 10, 10, 10);

        changeBrowser.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browser = browserInputField.getText();
                callbacks.saveExtensionSetting(BROWSER, browser);
            }
        });

        browserFrame.add(browserInputField);
        browserFrame.add(changeBrowser);

        changeURL.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchURL = urlInputField.getText();
                callbacks.saveExtensionSetting(SEARCH_URL, searchURL);
            }
        });

        urlFrame.add(urlInputField);
        urlFrame.add(changeURL);

        constraints.gridx = 0;
        constraints.gridy = 0;      
        panel.add(browserFrame, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;   
        panel.add(urlFrame, constraints);

        return panel;
    }
}