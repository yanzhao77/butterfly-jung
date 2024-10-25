/* 
 * Copyright (c) 2013, Tim Boudreau
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.softroad.butterflyjung.demo;


import com.google.common.base.Function;
import com.softroad.butterflyjung.JungConnectionWidget;
import com.softroad.butterflyjung.extensions.BaseJungScene;
import edu.uci.ics.jung.algorithms.layout.SpringLayout;
import edu.uci.ics.jung.algorithms.layout.*;
import edu.uci.ics.jung.graph.*;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    private static final Pattern QUOTES = Pattern.compile("\"(.*?)\"\\s+\"(.*?)\"\\s+");
    private static final Pattern NO_QUOTES = Pattern.compile("(.*?)\\s+(.*)");

    private static class GraphAndForest {

        private final ObservableGraph<String, String> graph;
        private final Forest<String, String> forest;

        public GraphAndForest(ObservableGraph<String, String> graph, Forest<String, String> forest) {
            this.graph = graph;
            this.forest = forest;
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }

        GraphAndForest gf = loadGraph(args);

        showDemo(gf);
    }

    public static JFrame showDemo(GraphAndForest gf) throws IOException {
        return showDemo(gf.graph, gf.forest);
    }

    public static <V, E> JFrame showDemo(Graph<V, E> graph) throws IOException {
        return showDemo(graph, null);
    }

    public static <V, E> JFrame showDemo(Graph<V, E> graph, Forest<V, E> forest) throws IOException {
        final JFrame jf = new JFrame("Visual Library + JUNG Demo");
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Layout layout;
        try {
            layout = forest == null ? new KKLayout(graph) : new TreeLayout(forest, 70, 70);
        } catch (IllegalArgumentException ex) {
            layout = new KKLayout(graph);
        }
        final BaseJungScene scene = new SceneImpl(graph, layout);
        jf.setLayout(new BorderLayout());
        jf.add(new JScrollPane(scene.createView()), BorderLayout.CENTER);

        JToolBar bar = new JToolBar();
        bar.setMargin(new Insets(5, 5, 5, 5));
        bar.setLayout(new FlowLayout(5));
        DefaultComboBoxModel<Layout> mdl = new DefaultComboBoxModel<>();
        mdl.addElement(new KKLayout(graph));
        mdl.addElement(layout);
        if (forest != null) {
            mdl.addElement(new BalloonLayout(forest));
            mdl.addElement(new RadialTreeLayout(forest));
        }
        mdl.addElement(new CircleLayout(graph));
        mdl.addElement(new FRLayout(graph));
        mdl.addElement(new FRLayout2(graph));
        mdl.addElement(new ISOMLayout(graph));
        mdl.addElement(new SpringLayout(graph));
        mdl.addElement(new SpringLayout2(graph));
        mdl.addElement(new DAGLayout(graph));
//        mdl.addElement(new XLayout(g));
        mdl.setSelectedItem(layout);
        final JCheckBox checkbox = new JCheckBox("Animate iterative layouts");

        scene.setLayoutAnimationFramesPerSecond(48);

        final JComboBox<Layout> layouts = new JComboBox(mdl);
        layouts.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jlist, Object o, int i, boolean bln, boolean bln1) {
                o = o.getClass().getSimpleName();
                return super.getListCellRendererComponent(jlist, o, i, bln, bln1); //To change body of generated methods, choose Tools | Templates.
            }
        });
        bar.add(new JLabel(" Layout Type"));
        bar.add(layouts);
        layouts.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                Layout layout = (Layout) layouts.getSelectedItem();
                // These two layouts implement IterativeContext, but they do
                // not evolve toward anything, they just randomly rearrange
                // themselves.  So disable animation for these.
                if (layout instanceof ISOMLayout || layout instanceof DAGLayout) {
                    checkbox.setSelected(false);
                }
                scene.setGraphLayout(layout, true);
            }
        });

        bar.add(new JLabel(" Connection Shape"));
        Function<E, Shape> first;
        DefaultComboBoxModel<Function<E, Shape>> shapes = new DefaultComboBoxModel<>();
        shapes.addElement(first = JungConnectionWidget.quadratic(scene.graph()));
        shapes.addElement(JungConnectionWidget.bent(scene.graph()));
        shapes.addElement(JungConnectionWidget.cubic(scene.graph()));
        shapes.addElement(JungConnectionWidget.line(scene.graph()));
        shapes.addElement(JungConnectionWidget.box(scene.graph()));
        shapes.addElement(JungConnectionWidget.orthagonal(scene.graph()));
        shapes.addElement(JungConnectionWidget.simpleLoop(scene.graph()));
        shapes.addElement(JungConnectionWidget.wedge(scene.graph()));

        final JComboBox<Function<E, Shape>> shapesBox = new JComboBox<>(shapes);
        shapesBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                Function<String, Shape> xform = (Function<String, Shape>) shapesBox.getSelectedItem();
                scene.setConnectionEdgeShape(xform);
            }
        });
        shapesBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> jlist, Object o, int i, boolean bln, boolean bln1) {
                o = o.getClass().getSimpleName();
                return super.getListCellRendererComponent(jlist, o, i, bln, bln1); //To change body of generated methods, choose Tools | Templates.
            }
        });
        shapesBox.setSelectedItem(first);
        bar.add(shapesBox);
        jf.add(bar, BorderLayout.NORTH);
        bar.add(new MinSizePanel(scene.createSatelliteView()));
        bar.setFloatable(false);
        bar.setRollover(true);

        final JLabel selectionLabel = new JLabel("<html>&nbsp;</html>");
        Lookup.Result<String> selectedNodes = scene.getLookup().lookupResult(String.class);
        selectedNodes.allInstances();
        LookupListener listener = new LookupListener() {
            @Override
            public void resultChanged(LookupEvent le) {
                Lookup.Result<String> res = (Lookup.Result<String>) le.getSource();
                StringBuilder sb = new StringBuilder("<html>");
                List<String> l = new ArrayList<>(res.allInstances());
                Collections.sort(l);
                for (String s : l) {
                    if (sb.length() != 6) {
                        sb.append(", ");
                    }
                    sb.append(s);
                }
                sb.append("</html>");
                selectionLabel.setText(sb.toString());
            }
        };
        selectionLabel.putClientProperty("ll", listener); // ensure it's not garbage collected
        selectionLabel.putClientProperty("lr", selectedNodes); // ensure it's not garbage collected
        selectedNodes.addLookupListener(listener);
        selectedNodes.allInstances();

        checkbox.setSelected(true);
        checkbox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                scene.setAnimateIterativeLayouts(checkbox.isSelected());
            }
        });
        bar.add(checkbox);
        bar.add(selectionLabel);
        selectionLabel.setHorizontalAlignment(SwingConstants.TRAILING);

//        jf.setSize(jf.getGraphicsConfiguration().getBounds().width - 120, 700);
        jf.setSize(new Dimension(1280, 720));
        jf.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jf.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent we) {
                scene.relayout(true);
                scene.validate();
            }
        });
        jf.setVisible(true);
        return jf;
    }

    private static class MinSizePanel extends JPanel {

        MinSizePanel(JComponent inner) {
            setLayout(new BorderLayout());
            add(inner, BorderLayout.CENTER);
            setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
        }

        public Dimension getPreferredSize() {
            Dimension result = super.getPreferredSize();
            result.height = 40;
            return result;
        }
    }

    private static String[] elements(String line, String[] into) {
        if (line.startsWith("#")) {
            return null;
        }
        Matcher m = QUOTES.matcher(line);
        if (m.find()) {
            into[0] = m.group(1);
            into[1] = m.group(2);
            return into;
        } else {
            m = NO_QUOTES.matcher(line);
            if (m.find()) {
                into[0] = m.group(1);
                into[1] = m.group(2);
                return into;
            }
        }
        return null;
    }

    private static GraphAndForest loadGraph(String[] args) throws FileNotFoundException, IOException {
        if (args.length > 0) {
            File f = new File(args[0]);
            if (!f.exists() || !f.isFile() || !f.canRead()) {
                System.err.println("File does not exist, is not readable, or is not a file: " + f);
                System.exit(1);
            }
            try {
                Forest<String, String> forest = new DelegateForest<>();
                String[] arr = new String[2];
                Set<String> pairs = new HashSet<>();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    int ix = 0;
                    for (String line; (line = br.readLine()) != null;) {
                        line = line.trim();
                        String[] items = elements(line, arr);
                        if (items != null) {
                            if (items[0].equals(items[1])) {
                                continue;
                            }
                            String[] x = new String[]{items[0], items[1]};
                            Arrays.sort(x);
                            String key = x[0] + "::" + x[1];
                            if (!pairs.contains(key)) {
                                String edge = Integer.toString(ix);
                                forest.addEdge(edge, items[0], items[1]);
                                pairs.add(key);
                            } else {
                                System.out.println("DUP: " + key);
                            }
                        }
                        ix++;
                    }
                }
                ObservableGraph<String, String> g = new ObservableGraph<>(forest);
                return new GraphAndForest(g, forest);
            } catch (Exception e) {
                // Graph has cycles - try undirected
                e.printStackTrace();
                UndirectedOrderedSparseMultigraph<String, String> graph = new UndirectedOrderedSparseMultigraph<String, String>();
                String[] arr = new String[2];
                Set<String> pairs = new HashSet<>();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    int ix = 0;
                    for (String line; (line = br.readLine()) != null;) {
                        line = line.trim();
                        String[] items = elements(line, arr);
                        if (items != null) {
                            if (items[0].equals(items[1])) {
                                continue;
                            }
                            String edge = Integer.toString(ix);
                            try {
                                String[] x = new String[]{items[0], items[1]};
                                Arrays.sort(x);
                                String key = x[0] + "::" + x[1];
                                if (!pairs.contains(key)) {
                                    graph.addEdge(edge, items[0], items[1]);
                                    pairs.add(key);
                                } else {
                                    System.out.println("DUP: " + key);
                                }
                            } catch (IllegalArgumentException ex) {
                                System.err.println(ex.getMessage());
                            }
                        }
                        ix++;
                    }
                }
                ObservableGraph<String, String> g = new ObservableGraph<>(graph);
                return new GraphAndForest(g, null);
            }
        } else {
            Forest<String, String> forest = new DelegateForest<>();
            ObservableGraph<String, String> g = new ObservableGraph(new BalloonLayoutDemo().createTree(forest));
            return new GraphAndForest(g, forest);
        }
    }
}
