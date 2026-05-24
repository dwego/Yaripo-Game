package yaripo;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int sw = screen.width;
            int sh = screen.height;

            JFrame frame = new JFrame("YARIPO");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setUndecorated(true);
            frame.setResizable(false);

            Game game = new Game(sw, sh);
            frame.add(game);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            frame.setVisible(true);

            game.requestFocusInWindow();
            game.startGame();
        });
    }
}
