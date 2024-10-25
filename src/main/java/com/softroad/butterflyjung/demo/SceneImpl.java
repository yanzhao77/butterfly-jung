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

import com.softroad.butterflyjung.MultiMoveAction;
import com.softroad.butterflyjung.ObjectSceneAdapter;
import com.softroad.butterflyjung.extensions.BaseJungScene;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.ObservableGraph;
import edu.uci.ics.jung.graph.util.Pair;
import org.netbeans.api.visual.model.ObjectSceneEvent;
import org.netbeans.api.visual.model.ObjectSceneEventType;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.util.RequestProcessor;

import java.awt.*;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
class SceneImpl<V, E> extends BaseJungScene<V, E> {

    private final LayerWidget edgeTooltipLayer = new LayerWidget(this);
    private final LabelWidget label = new LabelWidget(this);

    public SceneImpl(ObservableGraph<V, E> graph, Layout layout) throws IOException {
        super(graph, layout);
        addChild(edgeTooltipLayer);
        edgeTooltipLayer.addChild(label);
        addObjectSceneListener(new HoverListener(), ObjectSceneEventType.OBJECT_HOVER_CHANGED);
    }

    public SceneImpl(Graph<V, E> graph, Layout layout) throws IOException {
        super(graph, layout);
        addChild(edgeTooltipLayer);
        edgeTooltipLayer.addChild(label);
        addObjectSceneListener(new HoverListener(), ObjectSceneEventType.OBJECT_HOVER_CHANGED);
    }

    @Override
    protected Widget createNodeWidget(V node) {
        DemoWidget<V, E> w = new DemoWidget<>(this, node);
        w.setLabel(node + "");
        w.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        w.getActions().addAction(new MultiMoveAction(relatedProvider(), moveProvider()));
        return w;
    }

    private class HoverListener extends ObjectSceneAdapter implements Runnable {

        private final RequestProcessor.Task task = RequestProcessor.getDefault().create(this);
        private Widget widget;
        private Object hover;

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            if (!EventQueue.isDispatchThread()) {
                EventQueue.invokeLater(this);
            } else if (widget != null && hover != null) {
                Pair<V> endpoints = graph().getEndpoints((E) hover);
//                String lbl = "EDGE " + hover + " (" + endpoints.getFirst() + " -> " + endpoints.getSecond() + ")";
                Rectangle r = widget.getClientArea();
                Point p = new Point((int) r.getCenterX(), (int) r.getCenterY());
                label.setForeground(new Color(255, 255, 255, 0));
                label.setPreferredLocation(p);
//                label.setLabel(lbl);
                getSceneAnimator().animateForegroundColor(label, Color.black);
            }
        }

        @Override
        public void hoverChanged(ObjectSceneEvent event, Object previousHoveredObject, Object newHoveredObject) {
            if (newHoveredObject instanceof String) {
                hover = newHoveredObject;
                widget = findWidget(hover);
                task.schedule(750);
            } else {
                widget = null;
                hover = null;
                task.cancel();
                getSceneAnimator().animateForegroundColor(label, new Color(255, 255, 255, 0));
            }
        }
    }
}
