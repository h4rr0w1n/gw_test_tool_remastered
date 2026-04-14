package com.amhs.swim.test;

import com.amhs.swim.test.gui.TestFrame;
import com.amhs.swim.test.util.Logger;

import javax.swing.*;

/**
 * Main entry point of the AMHS/SWIM Gateway Test Tool.
 * Initializes the Swing GUI and starts the application.
 */
public class Main {
    public static void main(String[] args) {
        Logger.log("INFO", "Khởi động AMHS/SWIM Gateway Test Tool...");
        
        // Initialize and setup the Swing User Interface
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            TestFrame frame = new TestFrame();
            frame.setVisible(true);
        });
    }
}