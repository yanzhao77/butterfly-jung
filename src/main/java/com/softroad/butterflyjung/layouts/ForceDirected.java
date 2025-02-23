/*
 * Copyright (c) 2020, Tim Boudreau
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
package com.softroad.butterflyjung.layouts;

import com.mastfrog.bits.Bits;
import com.mastfrog.function.DoubleBiConsumer;
import com.mastfrog.function.IntBiConsumer;
import com.mastfrog.function.state.Int;
import com.mastfrog.geometry.Circle;
import com.mastfrog.geometry.EqLine;
import com.mastfrog.geometry.EqPointDouble;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.PairSet;
import com.mastfrog.util.collections.IntSet;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;

import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

/**
 * @author Tim Boudreau
 */
public class ForceDirected {

    final IntGraph graph;
    Bits[] neighbors;
    double[] forces;
    double[] xs;
    double[] ys;
    double[][] relatedness;
    final Random random;
    final int maxX;
    final int maxY;
    final int size;
    final double minRel;
    final double maxRel;

    public ForceDirected(IntGraph graph) {
        this(graph, 1536, 1024);
    }

    public ForceDirected(IntGraph graph, int maxX, int maxY) {
        this(graph, maxX, maxY, null);
    }

    public ForceDirected(IntGraph graph, int maxX, int maxY, Random random) {
        this.maxX = maxX;
        this.maxY = maxY;
        this.graph = graph;
        this.size = graph.size();
        if (random == null) {
            random = ThreadLocalRandom.current();
        }
        this.random = random;
        neighbors = new Bits[graph.size()];
        forces = new double[graph.size()];
        xs = new double[graph.size()];
        ys = new double[graph.size()];
        relatedness = new double[graph.size()][graph.size()];
        IntSet cards = initializePositions(maxX, maxY, graph);
        computeRelatedness();
        double[] minMax = initializeRelatedness();
        minRel = minMax[0];
        maxRel = minMax[1];
        int c = cards.size();
        int sz = Math.max(2, c / 3);
        Iterator<Integer> it = cards.iterator();
        for (int i = 0; i < c; i++) {
            int val = it.next();
            if (i >= c - sz) {
                maxCardinalities.add(val);
            }
        }
//        System.out.println("min relation " + minRel + " max " + maxRel);
        System.out.println("MAX CARDS " + maxCardinalities);
    }

    private IntSet initializePositions(int maxX1, int maxY1, IntGraph graph1) {
        double centerX = maxX1 / 2;
        double centerY = maxY1 / 2;
        IntSet cards = IntSet.create(graph1.size());
        for (int i = 0; i < graph1.size(); i++) {
            neighbors[i] = graph1.neighbors(i);
            cards.add(neighbors[i].cardinality());
            xs[i] = perturb(centerX, maxX1);
            ys[i] = perturb(centerY, maxY1);
        }
        return cards;
    }

    private double[] initializeRelatedness() {
        double maxRel = Double.MIN_VALUE;
        double minRel = Double.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i != j) {
                    System.out.println("Related " + i + "," + j + "\t" + relatedness[i][j]);
                    minRel = Math.min(minRel, relatedness[i][j]);
                    maxRel = Math.max(maxRel, relatedness[i][j]);
                }
            }
        }
        return new double[]{minRel, maxRel};
    }

    IntSet maxCardinalities = IntSet.create(100);

    static void sequential(int count, IntConsumer c) {
        for (int i = 0; i < count; i++) {
            c.accept(i);
        }
    }

    private void computeRelatedness() {
        sequential(graph.size(), a -> {
            sequential(graph.size(), b -> {
                if (a != b) {
                    List<IP> p1 = undirectedPathsBetween(a, b);
                    for (IP ip : p1) {
                        int sz = ip.size();
                        double val = sz - 1;
                        double c = curve(val);
                        relatedness[a][b] += c;
                    }
                }
            });
        });
    }

    int size() {
        return size;
    }

    static double CURVE_FACTOR = 1D;

    private double curve(double depth) {
        if (depth == 0) {
            return 1;
        }
        return 1D / depth;
//        return CURVE_FACTOR / (depth * 3D);
    }

    void setPosition(int index, double x, double y) {
        xs[index] = x;
        ys[index] = y;
    }

    private double perturb(double pos, double max) {
//        if (true) {
//            return pos;
//        }
        double rnd = random.nextDouble();
        double dir = random.nextBoolean() ? 1 : -1;
        return pos + ((max / 2D) * rnd * dir);
    }

    public static IntGraph testGraph() {
//        return sensorConfigLanguageRules();
//        return rustGrammarRules();
        return threeHubsConnectedMore();
//        return threeHubsConnected();
//        return twoHubs();
//        return triple();
//        return pair();
    }

    public static class Repul implements Force {

        private final double threshold;
        private final Circle circle;

        public Repul(double cx, double cy, double threshold) {
            this.threshold = threshold;
            circle = new Circle(cx, cy);
        }

        static double adjAngle(double a, double by) {
            a += by;
            if (a < 0) {
                a = 360D + a;
            } else if (a > 360) {
                a -= 360D;
            }
            return a;
        }

        @Override
        public void accept(double a, double b, DoubleBiConsumer transformed) {
            double dist = circle.distanceToCenter(a, b);
            if (dist < threshold) {
                double ang = circle.angleOf(a, b);
//                double tan = Math.toDegrees(Math.tan(Math.toRadians(ang)));
//                circle.positionOf(tan, transformed);

//                double newDist = dist + 3D;
//                double newDist = dist + 3D;
                double newDist = dist + 1D;

                double newAng = ang + 2;//adjAngle(ang, 2);

                circle.positionOf(newAng, newDist, transformed);
            } else {
                transformed.accept(a, b);
            }
        }
    }

    double ticks;

    void iterate() {
        resetStats();
        ticks++;
//        BitSet done = new BitSet(size * size);
        for (int i = 0; i < size; i++) {
            int ix = i;
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    continue;
                }
                int jx = j;
                double rel = relatedness[ix][jx];
                Force force = Force.NONE;
                int icard = neighbors[i].cardinality();
                int jcard = neighbors[j].cardinality();
                if (jcard == 1 && neighbors[j].get(i)) {
//                    parent must actuall be it
                    force = new Attraction(xs[i], ys[i], 60, 1, 0);
                } else if (jcard == 2 && graph.parents(jx).cardinality() == 1
                        && graph.parents(jx).get(ix)) {
                    force = new Attraction(xs[i], ys[i], 60, 1, 0);
                }
                if (jcard == 0) {
                    Circle circle = new Circle(maxX / 2, maxY / 2);
                    double[] pos = new double[2];
                    circle.positionOf((i * j) % 360, maxX, pos);
//                    System.out.println("SINGLE " + j + " to " + pos[0] + "," + pos[1]);
                    force = new SimpleForce(pos[0], pos[1], Strength.NO_DROPOFF.negate());
                }
                if (force != Force.NONE) {
                    force = damping.setDelegate(force);
                    force.accept(xs[j], ys[j], (a, b) -> {
                        if (!Double.isInfinite(a) && !Double.isNaN(a)) {
                            xs[jx] = updatingX(ix, a);
                        }
                        if (!Double.isInfinite(b) && !Double.isNaN(b)) {
                            ys[jx] = updatingY(ix, b);
                        }
                    });
                    continue;
                }
                if (maxCardinalities.contains(jcard) && maxCardinalities.contains(icard)) {
//                    Circle circle = new Circle(maxX / 2, maxY / 2);
//                    double[] pos = new double[2];
//                    circle.positionOf((i * j) % 360, maxX, pos);
//                    force = new SimpleForce(pos[0], pos[1], Strength.NO_DROPOFF);
                }
                if (icard < jcard && (jcard - icard) > 3) {
                    if (rel > 0.1) {
//                        double horizon = 60;
                        double horizon = Math.max(60D, 10D * (icard + 1D));

                        force = force.and(new Attraction(xs[j], ys[j], horizon, rel, minRel));
                        if (neighbors[j].intersects(neighbors[i])) {
                            force = force.and(new RotationalForce(xs[j], ys[j],
                                    Strength.INVERSE_SQUARE.negate(), 360));
                        }
                    }
                } else {
                    if (rel <= minRel * 3) {
                        force = new Repul(xs[j], ys[j], 80).and(force);
                    }
                }
                if (icard > 4 && jcard > 4 && rel < 0.175) {
//                    force = force.and(new Repul(xs[j], ys[j], 50));
                }
                if (maxCardinalities.contains(icard) && maxCardinalities.contains(jcard)) {
                    // High cardinality nodes should push each other apart hard
//                    force = new Repul(xs[j], ys[j], 500).or(force);
                    force = new SimpleForce(xs[j], ys[j],
                            Strength.LINEAR.multiply(rel > 1 ? rel * 4 : 4 * (1D / rel))
                                    .negate()
                                    .bound(500))
                            //                                                        .and(new RotationalForce(ys[j], xs[j], Strength.fixed(50), 400))
                            .and(force);
                } else {
                    force = new Repul(xs[j], ys[j], 25).or(force);
                }
//                done.set(i * j);

//                Force repel = new SimpleForce(xs[j], ys[j],
//                        Strength.LINEAR.cuberoot().bound(500).external());
//                force = damping.setDelegate(force);
                force.accept(xs[i], ys[i], (a, b) -> {
                    if (!Double.isInfinite(a) && !Double.isNaN(a)) {
                        xs[ix] = updatingX(ix, a);
                    }
                    if (!Double.isInfinite(b) && !Double.isNaN(b)) {
                        ys[ix] = updatingY(ix, b);
                    }
                });
            }
        }
        applyEdgeForces();
    }

    double currMinX = Double.MAX_VALUE;
    double currMaxX = Double.MIN_VALUE;

    double currMinY = Double.MAX_VALUE;
    double currMaxY = Double.MIN_VALUE;

    double minX() {
        return currMinX;
    }

    double maxX() {
        return currMaxX;
    }

    double minY() {
        return currMinY;
    }

    double maxY() {
        return currMaxY;
    }

    double xNorm(int ix) {
        return xs[ix] - currMinX;
    }

    double yNorm(int ix) {
        return ys[ix] - currMinY;
    }

    double maxPerturb = 0;

    double maxPerturb() {
        return maxPerturb;
    }

    double updatingX(int ix, double newVal) {
        double diff = Math.abs(newVal - xs[ix]);
        maxPerturb = Math.max(maxPerturb, diff);
        currMinX = Math.min(currMinX, newVal);
        currMaxX = Math.max(currMaxX, newVal);
        return newVal;
    }

    double updatingY(int ix, double newVal) {
        double diff = Math.abs(newVal - ys[ix]);
        maxPerturb = Math.max(maxPerturb, diff);
        currMinY = Math.min(currMinY, newVal);
        currMaxY = Math.max(currMaxY, newVal);
        return newVal;
    }

    void resetStats() {
        currMinX = currMinY = Double.MAX_VALUE;
        currMaxX = currMaxY = Double.MIN_VALUE;
    }

    Dimension currentSize() {
        int x;
        if (currMaxX > currMinX) {
            x = (int) (Math.ceil(currMaxX) - Math.floor(currMinX));
        } else {
            x = maxX;
        }
        int y;
        if (currMaxY > currMinY) {
            y = (int) (Math.ceil(currMaxY) - Math.floor(currMinY));
        } else {
            y = maxX;
        }
        return new Dimension(x, y);
    }

    public void setLocation(int ix, double x, double y) {
        xs[ix] = x;
        ys[ix] = y;
    }

    public double x(int ix) {
        return xs[ix];
    }

    public double y(int ix) {
        return ys[ix];
    }

    static final DampingFieldForce damping = new DampingFieldForce(Force.NONE);

    static double[] intersection(double x1, double y1, double x2, double y2,
                                 double x3, double y3, double x4, double y4) {
        // Line AB represented as a1x + b1y = c1
        double a1 = y2 - y2;
        double b1 = x1 - x2;
        double c1 = a1 * x1 + b1 * y1;

        // Line CD represented as a2x + b2y = c2
        double a2 = y4 - y3;
        double b2 = x3 - x4;
        double c2 = a2 * x3 + b2 * y3;

        double determinant = a1 * b2 - a2 * b1;

        if (determinant == 0) {
            // The lines are parallel. This is simplified
            // by returning a pair of FLT_MAX
            return null;
        } else {
            double x = (b2 * c1 - b1 * c2) / determinant;
            double y = (a1 * c2 - a2 * c1) / determinant;
            return new double[]{x, y};
        }
    }

    void applyEdgeForces() {
        if (true) {
            return;
        }
        PairSet ps = graph.allEdges();
        EqLine line1 = new EqLine();
        EqLine line2 = new EqLine();
        EqLine temp = new EqLine();
        Int handled = Int.create();
        double factor = -0.0125;
        ps.forEach((a, b) -> {
            PairSet withoutSecondNeighbors = ps.copy();
            withoutSecondNeighbors.remove(a, b);
            graph.neighbors(a).forEachSetBitAscending(an -> {
                withoutSecondNeighbors.remove(a, an);
            });
            graph.neighbors(b).forEachSetBitAscending(an -> {
                withoutSecondNeighbors.remove(a, an);
            });
            int h1 = handled.get();
            withoutSecondNeighbors.forEach((src1, dest1) -> {
                line1.x1 = xs[src1];
                line1.y1 = ys[src1];
                line1.x2 = xs[dest1];
                line1.y2 = ys[dest1];
                if (handled.getAsInt() > h1) {
                    return;
                }
                withoutSecondNeighbors.forEach((src2, dest2) -> {
                    if (anyEqual(src1, dest1, src2, dest2)) {
                        return;
                    }
                    line2.x1 = xs[src2];
                    line2.y1 = ys[src2];
                    line2.x2 = xs[dest2];
                    line2.y2 = ys[dest2];
                    if (line1.intersectsLine(line2)) {
                        EqPointDouble isect = line1.intersectionPoint(line2);
                        if (isect != null) {
//                            System.out.println("Isect " + src1 + ", " + dest1 + " and " + src2 + ", " + dest2);
//                            if ((src1 % 2 == 0) == (src2 % 2 == 0)) {
//                                line2.setLength(line2.length() + 0.025, false);
//                                xs[src2] = line2.x1;
//                                ys[src2] = line2.y1;
//                                xs[dest2] = line2.x2;
//                                ys[dest2] = line2.y2;
//                            } else {
//                                line2.setLength(line2.length() - 0.025, true);
//                                xs[src1] = line1.x1;
//                                ys[src1] = line1.y1;
//                                xs[dest1] = line1.x2;
//                                ys[dest1] = line1.y2;
//                            }

                            if ((src1 % 2 == 0) == (src2 % 2 != 0)) {
                                if (random.nextInt(7) != 1) {
                                    return;
                                }
                                temp.setLine(line1);
                                if (src1 % 2 == 0) {
                                    temp.shiftPerpendicular(3);
                                } else {
                                    temp.shiftPerpendicular(-3);
                                }
                                Circle circ
                                        = new Circle(line1.midPoint(), (line1.length() * 0.5) + (random.nextDouble() - 0.5));
                                double a1 = circ.angleOf(line1.startPoint());
                                double a2 = circ.angleOf(line1.endPoint());
//                                if (src1 % 2 == 0) {
//                                    a1 += 0.125;
//                                    a2 += 0.125;
//                                } else {
//                                    a1 -= 0.125;
//                                    a2 -= 0.125;
//                                }
                                circ.positionOf(a1, (x1b, y1b) -> {
                                    xs[src1] = x1b;
                                    ys[src1] = y1b;
                                });
                                circ.positionOf(a2, (x2b, y2b) -> {
                                    xs[dest1] = x2b;
                                    ys[dest1] = y2b;
                                });

//                                double subang = Angle.subtractAngles(line1.angle() % 180D, line2.angle() % 180D);
//                                if (Math.abs(subang) > 60 && Math.abs(subang) < 120) {
//                                    double angDir = line1.angle() > line2.angle() ? -1 : 1;
//                                    double adj = angDir * 0.00125;
//                                    temp.setAngle(temp.angle() + adj);
//                                    xs[src1] = temp.x1;
//                                    ys[src1] = temp.y1;
//                                    xs[dest1] = temp.x2;
//                                    ys[dest2] = temp.y2;
//                                    handled.increment();
//                                }
                            }

                            double distToStart = isect.distance(line1.x1, line1.y1);
                            double distToEnd = isect.distance(line1.x2, line1.y2);
                            double distToStart2 = isect.distance(line2.x1, line2.y1);
                            double distToEnd2 = isect.distance(line2.x2, line2.y2);

                            int pointToMove = least(restrict(distToStart),
                                    restrict(distToEnd), restrict(distToStart2),
                                    restrict(distToEnd2));
                            double change;
                            switch (pointToMove) {
                                case 0:
                                    if (line1.length() > THRESH) {
//                                        temp.setLine(line1);
//                                        temp.setPoint1(isect);
//                                        change = distToStart * factor;
//                                        temp.setLength(distToEnd + change, false);
//                                        xs[dest1] = temp.x2;
//                                        ys[dest1] = temp.y2;
                                    }
                                    break;
                                case 1:
                                    if (line1.length() > THRESH) {
                                        temp.setLine(line1);
//                                        double subang = Angle.subtractAngles(line1.angle() % 180D, line2.angle() % 180D);
//                                        if (Math.abs(subang) > 60 && Math.abs(subang) < 120) {
//                                            double angDir = line1.angle() > line2.angle() ? 1 : -1;
//                                            double adj = angDir * 0.00125;
//                                            temp.setAngle(temp.angle() + adj);
//                                            xs[src1] = temp.x1;
//                                            ys[src1] = temp.y1;
//                                            xs[dest1] = temp.x2;
//                                            ys[dest2] = temp.y2;
//                                        }

//                                        temp.setPoint2(isect);
//                                        temp.shiftPerpendicular(0.25);
//                                        change = distToEnd * factor;
//                                        temp.setLength(distToStart + change, true);
//                                        xs[src1] = temp.x1;
//                                        ys[src1] = temp.y1;
                                    }
                                    break;
                                case 2:
                                    if (line2.length() > THRESH) {
                                        temp.setLine(line2);
                                        temp.setPoint1(isect);
                                        temp.shiftPerpendicular(2.25);
                                        change = distToStart * factor;
                                        temp.setLength(distToEnd2 + change, false);
                                        xs[dest2] = temp.x2;
                                        ys[dest2] = temp.y2;
                                    }
                                    break;
                                case 3:
                                    if (line2.length() > THRESH) {
//                                        temp.setLine(line2);
//                                        temp.setPoint2(isect);
//                                        temp.shiftPerpendicular(-0.25);
//                                        change = distToStart2 * factor;
//                                        temp.setLength(distToStart2 + change, true);
//                                        xs[src2] = temp.x1;
//                                        ys[src2] = temp.y1;
                                    }
                                    break;
                            }
                        }
                    }
                });
                if (handled.getAsInt() > h1) {
                    return;
                }
            });
        });
    }

    static IntSet itest = IntSet.create(4);

    static boolean anyEqual(int... ints) {
        itest.clear();
        itest.addAll(ints);
        return itest.size() != ints.length;
    }

    static int THRESH = 360;

    static double restrict(double d) {
        if (true) {
            return d;
        }
        if (d <= THRESH) {
            return Double.MAX_VALUE;
        }
        return d;
    }

    private static int least(double... items) {
        double val = Double.MAX_VALUE;
        int target = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i] < val) {
                val = items[i];
                target = i;
            }
        }
        return target;
    }

    private static int greatest(double... items) {
        double val = Double.MIN_VALUE;
        int target = -1;
        for (int i = 0; i < items.length; i++) {
            if (items[i] > val) {
                val = items[i];
                target = i;
            }
        }
        return target;
    }

    void positions(PositionConsumer bi) {
        for (int i = 0; i < graph.size(); i++) {
            bi.accept(i, xs[i], ys[i]);
        }
    }

    void edges(EdgeConsumer bi) {
        for (int i = 0; i < graph.size(); i++) {
            final int ix = i;
            neighbors[i].forEachSetBitAscending(n -> {
                bi.accept(ix, xs[ix], ys[ix], xs[n], ys[n]);
            });
        }
    }

    int testPositions(PositionTester tester) {
        for (int i = 0; i < graph.size(); i++) {
            if (tester.accept(i, xs[i], ys[i])) {
                return i;
            }
        }
        return -1;
    }

    interface PositionConsumer {

        void accept(int node, double x, double y);
    }

    interface PositionTester {

        boolean accept(int node, double x, double y);
    }

    interface EdgeConsumer {

        void accept(int node, double x, double y, double x1, double y1);
    }

    public void show() {
        JFrame jf = new JFrame();
        jf.setDefaultCloseOperation(EXIT_ON_CLOSE);
        jf.setContentPane(new Gui(this));
        jf.pack();
        jf.setTitle("Graph Visualization");
        jf.setVisible(true);
    }

    public List<IP> undirectedPathsBetween(int src, int target) {
        List<IP> paths = new ArrayList<>();
        IP base = new IP().add(src);
        PairSet seenPairs = PairSet.fromIntArray(new int[size][size]);
        // If there is a direct edge, we will miss that, so add it now
        if (neighbors[src].get(target)) {
            seenPairs.add(src, target);
            paths.add(new IP().add(src).add(target));
        }
        pathsTo(src, target, base, paths, seenPairs);
        Collections.sort(paths);
        return paths;
    }

    private void pathsTo(int src, int target, IP base, List<? super IP> paths, PairSet seenPairs) {
        if (src == target) {
            paths.add(base.copy().add(target));
            return;
        }
        neighbors[src].forEachSetBitAscending(bit -> {
            if (seenPairs.contains(src, bit)) {
                return;
            }
            seenPairs.add(src, bit);
            if (bit == target) {
                IP found = base.copy().add(target);
                paths.add(found);
            } else {
                if (!base.contains(bit)) {
                    pathsTo(bit, target, base.copy().add(bit), paths, seenPairs);
                }
            }
        });
    }

    static IntGraph dirtSimple() {
        return fromPairs(new int[][]{
                {0, 1}, {0, 2}, {0, 3}
        });
    }

    static IntGraph pair() {
        return fromPairs(new int[][]{
                {0, 1}
        });
    }

    static IntGraph triple() {
        return fromPairs(new int[][]{
                {0, 1}, {1, 2}
        });
    }

    static IntGraph twoHubs() {
        /*
                2                          7  8
            1        2 ------------------6      9
                0                           11
             4    50                        10 --------------------12

         */
        int[][] pairs = new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {2, 6}, {6, 11}, {7, 11}, {8, 11}, {9, 11}, {10, 11}
//                ,
//        {11, 12}
        };
        return fromPairs(pairs);
    }

    static IntGraph threeHubs() {
        /*
                2                          7  8
            1        2 ------------------6      9
                0                           11
             4    50                        10 --------------------12

         */
        int[][] pairs = new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {2, 6}, {6, 11}, {7, 11}, {8, 11}, {9, 11}, {10, 11},
                {10, 12}, {12, 13}, {13, 14}, {13, 15}, {13, 16}, {13, 17},
                {17, 18}, {18, 19}
//                ,
//        {11, 12}
        };
        return fromPairs(pairs);
    }

    static IntGraph threeHubsConnected() {
        /*
                2                          7  8
            1        2 ------------------6      9
                0                           11
             4    50                        10 --------------------12

         */
        int[][] pairs = new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {2, 6}, {6, 11}, {7, 11}, {8, 11}, {9, 11}, {10, 11},
                {10, 12}, {12, 13}, {13, 14}, {13, 15}, {13, 16}, {13, 17},
                {17, 18}, {18, 19}, {19, 3}
//                ,
//        {11, 12}
        };
        return fromPairs(pairs);
    }

    static IntGraph threeHubsConnectedMore() {
        /*
                2                          7  8
            1        2 ------------------6      9
                0                           11
             4    50                        10 --------------------12

         */
        int[][] pairs = new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5},
                {2, 6}, {6, 11}, {7, 11}, {8, 11}, {9, 11}, {10, 11},
                {10, 12}, {12, 13}, {13, 14}, {13, 15}, {13, 16}, {13, 17},
                {17, 18}, {18, 19}, {19, 3}, {19, 10}, {20, 11}, {21, 11},
                {21, 22}, {22, 23}, {23, 24}, {24, 25}
//                ,
//        {11, 12}
        };
        return fromPairs(pairs);
    }

    static int max(int[][] pairs) {
        int result = 0;
        for (int i = 0; i < pairs.length; i++) {
            for (int j : pairs[i]) {
                result = Math.max(result, j);
            }
        }
        return result;
    }

    static IntGraph fromPairs(int[][] pairs) {
        int amt = max(pairs) + 1;
        BitSet[] parents = new BitSet[amt];
        BitSet[] children = new BitSet[amt];

        for (int i = 0; i < amt; i++) {
            parents[i] = new BitSet(amt);
            children[i] = new BitSet(amt);
        }

        IntBiConsumer edge = (a, b) -> {
            parents[b].set(a);
            children[a].set(b);
        };

        for (int i = 0; i < pairs.length; i++) {
            int[] pair = pairs[i];
            edge.accept(pair[0], pair[1]);
        }

        return IntGraph.create(children);
//        return IntGraph.create(parents, children);
    }

    static IntGraph sensorConfigLanguageRules() {
        BitSet[] parents = new BitSet[67];
        BitSet[] children = new BitSet[67];
        parents[0] = new BitSet();
        parents[0].set(54);
        parents[0].set(66);
        children[0] = new BitSet();
        parents[1] = new BitSet();
        children[1] = new BitSet();
        children[1].set(30);
        parents[2] = new BitSet();
        parents[2].set(38);
        children[2] = new BitSet();
        parents[3] = new BitSet();
        parents[3].set(4);
        parents[3].set(7);
        children[3] = new BitSet();
        parents[4] = new BitSet();
        parents[4].set(36);
        children[4] = new BitSet();
        children[4].set(3);
        parents[5] = new BitSet();
        parents[5].set(24);
        children[5] = new BitSet();
        parents[6] = new BitSet();
        parents[6].set(25);
        children[6] = new BitSet();
        parents[7] = new BitSet();
        parents[7].set(38);
        children[7] = new BitSet();
        children[7].set(3);
        children[7].set(8);
        parents[8] = new BitSet();
        parents[8].set(7);
        children[8] = new BitSet();
        parents[9] = new BitSet();
        parents[9].set(49);
        children[9] = new BitSet();
        parents[10] = new BitSet();
        parents[10].set(57);
        children[10] = new BitSet();
        parents[11] = new BitSet();
        parents[11].set(41);
        children[11] = new BitSet();
        parents[12] = new BitSet();
        parents[12].set(43);
        children[12] = new BitSet();
        parents[13] = new BitSet();
        parents[13].set(49);
        children[13] = new BitSet();
        parents[14] = new BitSet();
        parents[14].set(45);
        children[14] = new BitSet();
        parents[15] = new BitSet();
        parents[15].set(47);
        children[15] = new BitSet();
        parents[16] = new BitSet();
        parents[16].set(48);
        children[16] = new BitSet();
        parents[17] = new BitSet();
        parents[17].set(52);
        children[17] = new BitSet();
        parents[18] = new BitSet();
        parents[18].set(53);
        children[18] = new BitSet();
        parents[19] = new BitSet();
        parents[19].set(57);
        children[19] = new BitSet();
        parents[20] = new BitSet();
        parents[20].set(58);
        children[20] = new BitSet();
        parents[21] = new BitSet();
        parents[21].set(59);
        children[21] = new BitSet();
        parents[22] = new BitSet();
        parents[22].set(65);
        children[22] = new BitSet();
        parents[23] = new BitSet();
        parents[23].set(60);
        parents[23].set(66);
        children[23] = new BitSet();
        children[23].set(24);
        children[23].set(25);
        parents[24] = new BitSet();
        parents[24].set(23);
        children[24] = new BitSet();
        children[24].set(5);
        parents[25] = new BitSet();
        parents[25].set(23);
        children[25] = new BitSet();
        children[25].set(6);
        parents[26] = new BitSet();
        parents[26].set(52);
        children[26] = new BitSet();
        parents[27] = new BitSet();
        parents[27].set(50);
        children[27] = new BitSet();
        parents[28] = new BitSet();
        parents[28].set(52);
        parents[28].set(58);
        parents[28].set(62);
        children[28] = new BitSet();
        parents[29] = new BitSet();
        parents[29].set(56);
        children[29] = new BitSet();
        parents[30] = new BitSet();
        parents[30].set(1);
        children[30] = new BitSet();
        parents[31] = new BitSet();
        parents[31].set(52);
        children[31] = new BitSet();
        parents[32] = new BitSet();
        parents[32].set(39);
        parents[32].set(43);
        parents[32].set(53);
        parents[32].set(59);
        children[32] = new BitSet();
        parents[33] = new BitSet();
        parents[33].set(50);
        children[33] = new BitSet();
        parents[34] = new BitSet();
        children[34] = new BitSet();
        parents[35] = new BitSet();
        children[35] = new BitSet();
        parents[36] = new BitSet();
        parents[36].set(43);
        parents[36].set(46);
        parents[36].set(47);
        parents[36].set(50);
        parents[36].set(56);
        parents[36].set(57);
        parents[36].set(59);
        parents[36].set(66);
        children[36] = new BitSet();
        children[36].set(4);
        parents[37] = new BitSet();
        parents[37].set(64);
        parents[37].set(66);
        children[37] = new BitSet();
        parents[38] = new BitSet();
        children[38] = new BitSet();
        children[38].set(2);
        children[38].set(7);
        parents[39] = new BitSet();
        parents[39].set(40);
        children[39] = new BitSet();
        children[39].set(32);
        children[39].set(45);
        children[39].set(57);
        children[39].set(58);
        parents[40] = new BitSet();
        parents[40].set(42);
        children[40] = new BitSet();
        children[40].set(39);
        parents[41] = new BitSet();
        parents[41].set(61);
        children[41] = new BitSet();
        children[41].set(11);
        children[41].set(63);
        parents[42] = new BitSet();
        children[42] = new BitSet();
        children[42].set(40);
        children[42].set(44);
        children[42].set(52);
        children[42].set(55);
        children[42].set(59);
        parents[43] = new BitSet();
        parents[43].set(44);
        children[43] = new BitSet();
        children[43].set(12);
        children[43].set(32);
        children[43].set(36);
        children[43].set(49);
        children[43].set(64);
        parents[44] = new BitSet();
        parents[44].set(42);
        children[44] = new BitSet();
        children[44].set(43);
        parents[45] = new BitSet();
        parents[45].set(39);
        children[45] = new BitSet();
        children[45].set(14);
        children[45].set(60);
        parents[46] = new BitSet();
        parents[46].set(63);
        children[46] = new BitSet();
        children[46].set(36);
        parents[47] = new BitSet();
        parents[47].set(51);
        children[47] = new BitSet();
        children[47].set(15);
        children[47].set(36);
        parents[48] = new BitSet();
        parents[48].set(61);
        children[48] = new BitSet();
        children[48].set(16);
        children[48].set(63);
        parents[49] = new BitSet();
        parents[49].set(43);
        children[49] = new BitSet();
        children[49].set(9);
        children[49].set(13);
        parents[50] = new BitSet();
        parents[50].set(52);
        children[50] = new BitSet();
        children[50].set(27);
        children[50].set(33);
        children[50].set(36);
        children[50].set(63);
        parents[51] = new BitSet();
        parents[51].set(58);
        children[51] = new BitSet();
        children[51].set(47);
        children[51].set(54);
        parents[52] = new BitSet();
        parents[52].set(42);
        children[52] = new BitSet();
        children[52].set(17);
        children[52].set(26);
        children[52].set(28);
        children[52].set(31);
        children[52].set(50);
        children[52].set(64);
        parents[53] = new BitSet();
        parents[53].set(55);
        children[53] = new BitSet();
        children[53].set(18);
        children[53].set(32);
        children[53].set(54);
        children[53].set(62);
        parents[54] = new BitSet();
        parents[54].set(51);
        parents[54].set(53);
        parents[54].set(57);
        children[54] = new BitSet();
        children[54].set(0);
        parents[55] = new BitSet();
        parents[55].set(42);
        children[55] = new BitSet();
        children[55].set(53);
        parents[56] = new BitSet();
        parents[56].set(63);
        children[56] = new BitSet();
        children[56].set(29);
        children[56].set(36);
        parents[57] = new BitSet();
        parents[57].set(39);
        children[57] = new BitSet();
        children[57].set(10);
        children[57].set(19);
        children[57].set(36);
        children[57].set(54);
        children[57].set(63);
        parents[58] = new BitSet();
        parents[58].set(39);
        children[58] = new BitSet();
        children[58].set(20);
        children[58].set(28);
        children[58].set(51);
        children[58].set(64);
        parents[59] = new BitSet();
        parents[59].set(42);
        children[59] = new BitSet();
        children[59].set(21);
        children[59].set(32);
        children[59].set(36);
        parents[60] = new BitSet();
        parents[60].set(45);
        children[60] = new BitSet();
        children[60].set(23);
        parents[61] = new BitSet();
        parents[61].set(62);
        children[61] = new BitSet();
        children[61].set(41);
        children[61].set(48);
        children[61].set(65);
        parents[62] = new BitSet();
        parents[62].set(53);
        children[62] = new BitSet();
        children[62].set(28);
        children[62].set(61);
        parents[63] = new BitSet();
        parents[63].set(41);
        parents[63].set(48);
        parents[63].set(50);
        parents[63].set(57);
        parents[63].set(65);
        children[63] = new BitSet();
        children[63].set(46);
        children[63].set(56);
        children[63].set(64);
        parents[64] = new BitSet();
        parents[64].set(43);
        parents[64].set(52);
        parents[64].set(58);
        parents[64].set(63);
        children[64] = new BitSet();
        children[64].set(37);
        parents[65] = new BitSet();
        parents[65].set(61);
        children[65] = new BitSet();
        children[65].set(22);
        children[65].set(63);
        parents[66] = new BitSet();
        children[66] = new BitSet();
        children[66].set(0);
        children[66].set(23);
        children[66].set(36);
        children[66].set(37);
        return IntGraph.create(parents, children);
    }

    static IntGraph rustGrammarRules() {
        // Rule graph of Rust.g4
        BitSet[] parents = new BitSet[252];
        BitSet[] children = new BitSet[252];
        parents[0] = new BitSet();
        parents[0].set(166);
        parents[0].set(213);
        parents[0].set(214);
        parents[0].set(215);
        parents[0].set(242);
        children[0] = new BitSet();
        parents[1] = new BitSet();
        parents[1].set(220);
        children[1] = new BitSet();
        parents[2] = new BitSet();
        parents[2].set(185);
        parents[2].set(196);
        parents[2].set(231);
        parents[2].set(238);
        parents[2].set(241);
        children[2] = new BitSet();
        parents[3] = new BitSet();
        parents[3].set(168);
        children[3] = new BitSet();
        parents[4] = new BitSet();
        parents[4].set(168);
        children[4] = new BitSet();
        parents[5] = new BitSet();
        parents[5].set(168);
        children[5] = new BitSet();
        parents[6] = new BitSet();
        parents[6].set(168);
        children[6] = new BitSet();
        parents[7] = new BitSet();
        parents[7].set(168);
        children[7] = new BitSet();
        parents[8] = new BitSet();
        parents[8].set(168);
        children[8] = new BitSet();
        parents[9] = new BitSet();
        parents[9].set(168);
        children[9] = new BitSet();
        parents[10] = new BitSet();
        parents[10].set(168);
        children[10] = new BitSet();
        parents[11] = new BitSet();
        parents[11].set(168);
        children[11] = new BitSet();
        parents[12] = new BitSet();
        children[12] = new BitSet();
        parents[13] = new BitSet();
        parents[13].set(23);
        parents[13].set(164);
        parents[13].set(207);
        parents[13].set(242);
        children[13] = new BitSet();
        parents[14] = new BitSet();
        children[14] = new BitSet();
        parents[15] = new BitSet();
        parents[15].set(212);
        children[15] = new BitSet();
        parents[16] = new BitSet();
        children[16] = new BitSet();
        parents[17] = new BitSet();
        parents[17].set(18);
        parents[17].set(29);
        children[17] = new BitSet();
        children[17].set(20);
        children[17].set(37);
        children[17].set(126);
        children[17].set(128);
        children[17].set(139);
        parents[18] = new BitSet();
        parents[18].set(31);
        children[18] = new BitSet();
        children[18].set(17);
        children[18].set(107);
        parents[19] = new BitSet();
        parents[19].set(107);
        parents[19].set(128);
        children[19] = new BitSet();
        parents[20] = new BitSet();
        parents[20].set(17);
        parents[20].set(171);
        parents[20].set(179);
        parents[20].set(206);
        children[20] = new BitSet();
        parents[21] = new BitSet();
        parents[21].set(179);
        parents[21].set(197);
        children[21] = new BitSet();
        children[21].set(43);
        parents[22] = new BitSet();
        parents[22].set(72);
        children[22] = new BitSet();
        parents[23] = new BitSet();
        parents[23].set(23);
        children[23] = new BitSet();
        children[23].set(13);
        children[23].set(23);
        children[23].set(24);
        children[23].set(138);
        parents[24] = new BitSet();
        parents[24].set(23);
        children[24] = new BitSet();
        parents[25] = new BitSet();
        parents[25].set(198);
        children[25] = new BitSet();
        parents[26] = new BitSet();
        parents[26].set(165);
        parents[26].set(171);
        parents[26].set(203);
        children[26] = new BitSet();
        children[26].set(67);
        children[26].set(147);
        parents[27] = new BitSet();
        children[27] = new BitSet();
        parents[28] = new BitSet();
        children[28] = new BitSet();
        parents[29] = new BitSet();
        parents[29].set(165);
        parents[29].set(203);
        children[29] = new BitSet();
        children[29].set(17);
        children[29].set(30);
        children[29].set(114);
        children[29].set(137);
        parents[30] = new BitSet();
        parents[30].set(29);
        children[30] = new BitSet();
        parents[31] = new BitSet();
        children[31] = new BitSet();
        children[31].set(18);
        children[31].set(32);
        children[31].set(114);
        children[31].set(115);
        parents[32] = new BitSet();
        parents[32].set(31);
        children[32] = new BitSet();
        parents[33] = new BitSet();
        parents[33].set(36);
        parents[33].set(129);
        children[33] = new BitSet();
        children[33].set(125);
        children[33].set(128);
        parents[34] = new BitSet();
        parents[34].set(107);
        children[34] = new BitSet();
        parents[35] = new BitSet();
        parents[35].set(198);
        children[35] = new BitSet();
        parents[36] = new BitSet();
        children[36] = new BitSet();
        children[36].set(33);
        children[36].set(114);
        children[36].set(137);
        parents[37] = new BitSet();
        parents[37].set(17);
        parents[37].set(204);
        children[37] = new BitSet();
        parents[38] = new BitSet();
        parents[38].set(215);
        parents[38].set(228);
        parents[38].set(229);
        parents[38].set(248);
        children[38] = new BitSet();
        parents[39] = new BitSet();
        parents[39].set(165);
        parents[39].set(175);
        parents[39].set(180);
        parents[39].set(181);
        parents[39].set(199);
        parents[39].set(202);
        parents[39].set(208);
        parents[39].set(210);
        parents[39].set(214);
        parents[39].set(226);
        parents[39].set(227);
        parents[39].set(230);
        parents[39].set(232);
        parents[39].set(238);
        parents[39].set(244);
        parents[39].set(245);
        children[39] = new BitSet();
        parents[40] = new BitSet();
        children[40] = new BitSet();
        parents[41] = new BitSet();
        children[41] = new BitSet();
        parents[42] = new BitSet();
        parents[42].set(183);
        parents[42].set(250);
        children[42] = new BitSet();
        parents[43] = new BitSet();
        parents[43].set(21);
        parents[43].set(69);
        parents[43].set(72);
        parents[43].set(100);
        parents[43].set(118);
        parents[43].set(119);
        children[43] = new BitSet();
        parents[44] = new BitSet();
        children[44] = new BitSet();
        parents[45] = new BitSet();
        parents[45].set(176);
        children[45] = new BitSet();
        children[45].set(46);
        parents[46] = new BitSet();
        parents[46].set(45);
        children[46] = new BitSet();
        parents[47] = new BitSet();
        children[47] = new BitSet();
        parents[48] = new BitSet();
        parents[48].set(69);
        parents[48].set(179);
        parents[48].set(247);
        children[48] = new BitSet();
        parents[49] = new BitSet();
        parents[49].set(118);
        parents[49].set(228);
        children[49] = new BitSet();
        parents[50] = new BitSet();
        parents[50].set(119);
        children[50] = new BitSet();
        parents[51] = new BitSet();
        parents[51].set(204);
        children[51] = new BitSet();
        parents[52] = new BitSet();
        children[52] = new BitSet();
        parents[53] = new BitSet();
        parents[53].set(190);
        parents[53].set(217);
        parents[53].set(218);
        children[53] = new BitSet();
        parents[54] = new BitSet();
        parents[54].set(173);
        children[54] = new BitSet();
        parents[55] = new BitSet();
        parents[55].set(172);
        parents[55].set(204);
        children[55] = new BitSet();
        parents[56] = new BitSet();
        parents[56].set(213);
        children[56] = new BitSet();
        parents[57] = new BitSet();
        parents[57].set(69);
        children[57] = new BitSet();
        children[57].set(155);
        parents[58] = new BitSet();
        parents[58].set(194);
        children[58] = new BitSet();
        parents[59] = new BitSet();
        parents[59].set(177);
        children[59] = new BitSet();
        children[59].set(60);
        parents[60] = new BitSet();
        parents[60].set(59);
        children[60] = new BitSet();
        parents[61] = new BitSet();
        children[61] = new BitSet();
        parents[62] = new BitSet();
        parents[62].set(168);
        parents[62].set(193);
        parents[62].set(210);
        parents[62].set(240);
        children[62] = new BitSet();
        parents[63] = new BitSet();
        parents[63].set(181);
        parents[63].set(183);
        children[63] = new BitSet();
        parents[64] = new BitSet();
        parents[64].set(66);
        parents[64].set(187);
        parents[64].set(198);
        children[64] = new BitSet();
        parents[65] = new BitSet();
        parents[65].set(66);
        parents[65].set(187);
        parents[65].set(198);
        children[65] = new BitSet();
        parents[66] = new BitSet();
        children[66] = new BitSet();
        children[66].set(64);
        children[66].set(65);
        parents[67] = new BitSet();
        parents[67].set(26);
        children[67] = new BitSet();
        parents[68] = new BitSet();
        parents[68].set(175);
        parents[68].set(209);
        children[68] = new BitSet();
        parents[69] = new BitSet();
        parents[69].set(186);
        children[69] = new BitSet();
        children[69].set(43);
        children[69].set(48);
        children[69].set(57);
        parents[70] = new BitSet();
        parents[70].set(192);
        children[70] = new BitSet();
        parents[71] = new BitSet();
        parents[71].set(188);
        children[71] = new BitSet();
        parents[72] = new BitSet();
        parents[72].set(197);
        children[72] = new BitSet();
        children[72].set(22);
        children[72].set(43);
        children[72].set(75);
        children[72].set(82);
        children[72].set(108);
        children[72].set(155);
        parents[73] = new BitSet();
        parents[73].set(173);
        children[73] = new BitSet();
        parents[74] = new BitSet();
        parents[74].set(115);
        parents[74].set(117);
        children[74] = new BitSet();
        parents[75] = new BitSet();
        parents[75].set(72);
        children[75] = new BitSet();
        parents[76] = new BitSet();
        parents[76].set(198);
        parents[76].set(222);
        children[76] = new BitSet();
        parents[77] = new BitSet();
        parents[77].set(198);
        parents[77].set(222);
        children[77] = new BitSet();
        parents[78] = new BitSet();
        parents[78].set(198);
        parents[78].set(222);
        children[78] = new BitSet();
        parents[79] = new BitSet();
        parents[79].set(198);
        parents[79].set(222);
        children[79] = new BitSet();
        parents[80] = new BitSet();
        parents[80].set(198);
        parents[80].set(222);
        children[80] = new BitSet();
        parents[81] = new BitSet();
        parents[81].set(84);
        parents[81].set(95);
        children[81] = new BitSet();
        children[81].set(162);
        children[81].set(163);
        parents[82] = new BitSet();
        parents[82].set(72);
        children[82] = new BitSet();
        parents[83] = new BitSet();
        parents[83].set(198);
        parents[83].set(222);
        children[83] = new BitSet();
        parents[84] = new BitSet();
        parents[84].set(178);
        parents[84].set(183);
        parents[84].set(190);
        parents[84].set(191);
        parents[84].set(206);
        parents[84].set(210);
        parents[84].set(211);
        parents[84].set(217);
        parents[84].set(218);
        parents[84].set(226);
        parents[84].set(228);
        parents[84].set(229);
        parents[84].set(233);
        parents[84].set(238);
        parents[84].set(243);
        parents[84].set(250);
        children[84] = new BitSet();
        children[84].set(81);
        parents[85] = new BitSet();
        parents[85].set(193);
        parents[85].set(194);
        parents[85].set(209);
        children[85] = new BitSet();
        parents[86] = new BitSet();
        children[86] = new BitSet();
        parents[87] = new BitSet();
        parents[87].set(188);
        parents[87].set(250);
        children[87] = new BitSet();
        parents[88] = new BitSet();
        parents[88].set(195);
        children[88] = new BitSet();
        parents[89] = new BitSet();
        parents[89].set(173);
        parents[89].set(202);
        parents[89].set(231);
        children[89] = new BitSet();
        parents[90] = new BitSet();
        parents[90].set(169);
        parents[90].set(181);
        parents[90].set(208);
        parents[90].set(211);
        parents[90].set(226);
        parents[90].set(227);
        parents[90].set(235);
        parents[90].set(238);
        children[90] = new BitSet();
        parents[91] = new BitSet();
        parents[91].set(165);
        parents[91].set(242);
        children[91] = new BitSet();
        parents[92] = new BitSet();
        parents[92].set(171);
        parents[92].set(179);
        parents[92].set(180);
        parents[92].set(190);
        parents[92].set(192);
        parents[92].set(206);
        parents[92].set(210);
        parents[92].set(226);
        parents[92].set(230);
        parents[92].set(232);
        parents[92].set(233);
        parents[92].set(244);
        parents[92].set(245);
        parents[92].set(250);
        children[92] = new BitSet();
        parents[93] = new BitSet();
        parents[93].set(173);
        children[93] = new BitSet();
        parents[94] = new BitSet();
        parents[94].set(193);
        parents[94].set(240);
        children[94] = new BitSet();
        parents[95] = new BitSet();
        parents[95].set(201);
        children[95] = new BitSet();
        children[95].set(81);
        children[95].set(137);
        children[95].set(140);
        parents[96] = new BitSet();
        children[96] = new BitSet();
        children[96].set(97);
        parents[97] = new BitSet();
        parents[97].set(96);
        children[97] = new BitSet();
        parents[98] = new BitSet();
        parents[98].set(205);
        children[98] = new BitSet();
        parents[99] = new BitSet();
        parents[99].set(207);
        children[99] = new BitSet();
        parents[100] = new BitSet();
        parents[100].set(209);
        children[100] = new BitSet();
        children[100].set(43);
        children[100].set(146);
        parents[101] = new BitSet();
        parents[101].set(164);
        parents[101].set(179);
        parents[101].set(197);
        children[101] = new BitSet();
        parents[102] = new BitSet();
        parents[102].set(211);
        children[102] = new BitSet();
        parents[103] = new BitSet();
        parents[103].set(172);
        children[103] = new BitSet();
        parents[104] = new BitSet();
        parents[104].set(166);
        parents[104].set(213);
        parents[104].set(246);
        children[104] = new BitSet();
        parents[105] = new BitSet();
        parents[105].set(107);
        children[105] = new BitSet();
        parents[106] = new BitSet();
        parents[106].set(173);
        children[106] = new BitSet();
        parents[107] = new BitSet();
        parents[107].set(18);
        parents[107].set(129);
        children[107] = new BitSet();
        children[107].set(19);
        children[107].set(34);
        children[107].set(105);
        children[107].set(137);
        parents[108] = new BitSet();
        parents[108].set(72);
        children[108] = new BitSet();
        parents[109] = new BitSet();
        parents[109].set(164);
        children[109] = new BitSet();
        parents[110] = new BitSet();
        parents[110].set(172);
        parents[110].set(209);
        children[110] = new BitSet();
        parents[111] = new BitSet();
        parents[111].set(164);
        children[111] = new BitSet();
        parents[112] = new BitSet();
        parents[112].set(211);
        parents[112].set(239);
        parents[112].set(249);
        children[112] = new BitSet();
        parents[113] = new BitSet();
        children[113] = new BitSet();
        parents[114] = new BitSet();
        parents[114].set(29);
        parents[114].set(31);
        parents[114].set(36);
        parents[114].set(115);
        parents[114].set(117);
        parents[114].set(141);
        children[114] = new BitSet();
        parents[115] = new BitSet();
        parents[115].set(31);
        parents[115].set(115);
        children[115] = new BitSet();
        children[115].set(74);
        children[115].set(114);
        children[115].set(115);
        parents[116] = new BitSet();
        parents[116].set(117);
        children[116] = new BitSet();
        parents[117] = new BitSet();
        parents[117].set(117);
        parents[117].set(141);
        children[117] = new BitSet();
        children[117].set(74);
        children[117].set(114);
        children[117].set(116);
        children[117].set(117);
        parents[118] = new BitSet();
        parents[118].set(188);
        parents[118].set(209);
        children[118] = new BitSet();
        children[118].set(43);
        children[118].set(49);
        parents[119] = new BitSet();
        parents[119].set(188);
        children[119] = new BitSet();
        children[119].set(43);
        children[119].set(50);
        parents[120] = new BitSet();
        parents[120].set(241);
        children[120] = new BitSet();
        parents[121] = new BitSet();
        parents[121].set(241);
        children[121] = new BitSet();
        parents[122] = new BitSet();
        parents[122].set(246);
        children[122] = new BitSet();
        parents[123] = new BitSet();
        parents[123].set(219);
        children[123] = new BitSet();
        parents[124] = new BitSet();
        parents[124].set(173);
        parents[124].set(202);
        parents[124].set(231);
        children[124] = new BitSet();
        parents[125] = new BitSet();
        parents[125].set(33);
        parents[125].set(169);
        parents[125].set(208);
        parents[125].set(211);
        parents[125].set(226);
        parents[125].set(227);
        parents[125].set(235);
        parents[125].set(238);
        children[125] = new BitSet();
        parents[126] = new BitSet();
        parents[126].set(17);
        parents[126].set(165);
        parents[126].set(195);
        parents[126].set(212);
        parents[126].set(242);
        children[126] = new BitSet();
        parents[127] = new BitSet();
        parents[127].set(171);
        parents[127].set(179);
        parents[127].set(180);
        parents[127].set(190);
        parents[127].set(192);
        parents[127].set(206);
        parents[127].set(210);
        parents[127].set(226);
        parents[127].set(230);
        parents[127].set(232);
        parents[127].set(233);
        parents[127].set(244);
        parents[127].set(245);
        parents[127].set(250);
        children[127] = new BitSet();
        parents[128] = new BitSet();
        parents[128].set(17);
        parents[128].set(33);
        children[128] = new BitSet();
        children[128].set(19);
        parents[129] = new BitSet();
        parents[129].set(141);
        children[129] = new BitSet();
        children[129].set(33);
        children[129].set(107);
        parents[130] = new BitSet();
        children[130] = new BitSet();
        parents[131] = new BitSet();
        children[131] = new BitSet();
        parents[132] = new BitSet();
        parents[132].set(218);
        children[132] = new BitSet();
        parents[133] = new BitSet();
        parents[133].set(214);
        children[133] = new BitSet();
        parents[134] = new BitSet();
        parents[134].set(182);
        parents[134].set(184);
        parents[134].set(223);
        parents[134].set(226);
        parents[134].set(239);
        children[134] = new BitSet();
        parents[135] = new BitSet();
        parents[135].set(221);
        children[135] = new BitSet();
        parents[136] = new BitSet();
        parents[136].set(221);
        children[136] = new BitSet();
        parents[137] = new BitSet();
        parents[137].set(29);
        parents[137].set(36);
        parents[137].set(95);
        parents[137].set(107);
        children[137] = new BitSet();
        parents[138] = new BitSet();
        parents[138].set(23);
        parents[138].set(164);
        children[138] = new BitSet();
        parents[139] = new BitSet();
        parents[139].set(17);
        children[139] = new BitSet();
        parents[140] = new BitSet();
        parents[140].set(95);
        children[140] = new BitSet();
        parents[141] = new BitSet();
        parents[141].set(165);
        parents[141].set(203);
        children[141] = new BitSet();
        children[141].set(114);
        children[141].set(117);
        children[141].set(129);
        parents[142] = new BitSet();
        parents[142].set(226);
        children[142] = new BitSet();
        parents[143] = new BitSet();
        parents[143].set(250);
        children[143] = new BitSet();
        parents[144] = new BitSet();
        parents[144].set(218);
        children[144] = new BitSet();
        parents[145] = new BitSet();
        children[145] = new BitSet();
        parents[146] = new BitSet();
        parents[146].set(100);
        children[146] = new BitSet();
        parents[147] = new BitSet();
        parents[147].set(26);
        children[147] = new BitSet();
        parents[148] = new BitSet();
        children[148] = new BitSet();
        parents[149] = new BitSet();
        parents[149].set(198);
        parents[149].set(236);
        children[149] = new BitSet();
        parents[150] = new BitSet();
        parents[150].set(198);
        parents[150].set(236);
        children[150] = new BitSet();
        parents[151] = new BitSet();
        parents[151].set(198);
        parents[151].set(236);
        children[151] = new BitSet();
        parents[152] = new BitSet();
        parents[152].set(198);
        parents[152].set(236);
        children[152] = new BitSet();
        parents[153] = new BitSet();
        parents[153].set(198);
        parents[153].set(236);
        children[153] = new BitSet();
        parents[154] = new BitSet();
        parents[154].set(198);
        parents[154].set(236);
        children[154] = new BitSet();
        parents[155] = new BitSet();
        parents[155].set(57);
        parents[155].set(72);
        parents[155].set(175);
        parents[155].set(244);
        parents[155].set(248);
        children[155] = new BitSet();
        parents[156] = new BitSet();
        children[156] = new BitSet();
        parents[157] = new BitSet();
        parents[157].set(234);
        parents[157].set(235);
        children[157] = new BitSet();
        parents[158] = new BitSet();
        parents[158].set(239);
        children[158] = new BitSet();
        parents[159] = new BitSet();
        children[159] = new BitSet();
        parents[160] = new BitSet();
        parents[160].set(251);
        children[160] = new BitSet();
        parents[161] = new BitSet();
        children[161] = new BitSet();
        parents[162] = new BitSet();
        parents[162].set(81);
        children[162] = new BitSet();
        parents[163] = new BitSet();
        parents[163].set(81);
        children[163] = new BitSet();
        parents[164] = new BitSet();
        parents[164].set(179);
        children[164] = new BitSet();
        children[164].set(13);
        children[164].set(101);
        children[164].set(109);
        children[164].set(111);
        children[164].set(138);
        parents[165] = new BitSet();
        parents[165].set(203);
        children[165] = new BitSet();
        children[165].set(26);
        children[165].set(29);
        children[165].set(39);
        children[165].set(91);
        children[165].set(126);
        children[165].set(141);
        children[165].set(179);
        children[165].set(186);
        children[165].set(197);
        parents[166] = new BitSet();
        parents[166].set(240);
        children[166] = new BitSet();
        children[166].set(0);
        children[166].set(104);
        parents[167] = new BitSet();
        parents[167].set(179);
        parents[167].set(225);
        children[167] = new BitSet();
        children[167].set(168);
        children[167].set(179);
        children[167].set(242);
        parents[168] = new BitSet();
        parents[168].set(167);
        children[168] = new BitSet();
        children[168].set(3);
        children[168].set(4);
        children[168].set(5);
        children[168].set(6);
        children[168].set(7);
        children[168].set(8);
        children[168].set(9);
        children[168].set(10);
        children[168].set(11);
        children[168].set(62);
        parents[169] = new BitSet();
        parents[169].set(170);
        parents[169].set(172);
        parents[169].set(175);
        parents[169].set(188);
        parents[169].set(189);
        parents[169].set(193);
        parents[169].set(194);
        parents[169].set(205);
        parents[169].set(209);
        parents[169].set(224);
        parents[169].set(234);
        parents[169].set(251);
        children[169] = new BitSet();
        children[169].set(90);
        children[169].set(125);
        children[169].set(177);
        children[169].set(179);
        children[169].set(180);
        children[169].set(195);
        children[169].set(224);
        parents[170] = new BitSet();
        parents[170].set(172);
        children[170] = new BitSet();
        children[170].set(169);
        children[170].set(179);
        parents[171] = new BitSet();
        parents[171].set(171);
        parents[171].set(194);
        parents[171].set(209);
        parents[171].set(251);
        children[171] = new BitSet();
        children[171].set(20);
        children[171].set(26);
        children[171].set(92);
        children[171].set(127);
        children[171].set(171);
        children[171].set(173);
        children[171].set(179);
        parents[172] = new BitSet();
        parents[172].set(179);
        children[172] = new BitSet();
        children[172].set(55);
        children[172].set(103);
        children[172].set(110);
        children[172].set(169);
        children[172].set(170);
        children[172].set(179);
        children[172].set(202);
        children[172].set(212);
        children[172].set(214);
        children[172].set(220);
        parents[173] = new BitSet();
        parents[173].set(171);
        parents[173].set(179);
        children[173] = new BitSet();
        children[173].set(54);
        children[173].set(73);
        children[173].set(89);
        children[173].set(93);
        children[173].set(106);
        children[173].set(124);
        parents[174] = new BitSet();
        children[174] = new BitSet();
        children[174].set(200);
        parents[175] = new BitSet();
        parents[175].set(208);
        children[175] = new BitSet();
        children[175].set(39);
        children[175].set(68);
        children[175].set(155);
        children[175].set(169);
        children[175].set(179);
        children[175].set(225);
        parents[176] = new BitSet();
        parents[176].set(192);
        parents[176].set(211);
        parents[176].set(226);
        parents[176].set(229);
        children[176] = new BitSet();
        children[176].set(45);
        parents[177] = new BitSet();
        parents[177].set(169);
        children[177] = new BitSet();
        children[177].set(59);
        parents[178] = new BitSet();
        children[178] = new BitSet();
        children[178].set(84);
        children[178].set(216);
        parents[179] = new BitSet();
        parents[179].set(165);
        parents[179].set(167);
        parents[179].set(169);
        parents[179].set(170);
        parents[179].set(171);
        parents[179].set(172);
        parents[179].set(175);
        parents[179].set(179);
        parents[179].set(180);
        parents[179].set(188);
        parents[179].set(193);
        parents[179].set(199);
        parents[179].set(207);
        parents[179].set(209);
        parents[179].set(219);
        parents[179].set(228);
        parents[179].set(230);
        parents[179].set(235);
        parents[179].set(240);
        parents[179].set(242);
        children[179] = new BitSet();
        children[179].set(20);
        children[179].set(21);
        children[179].set(48);
        children[179].set(92);
        children[179].set(101);
        children[179].set(127);
        children[179].set(164);
        children[179].set(167);
        children[179].set(172);
        children[179].set(173);
        children[179].set(179);
        children[179].set(190);
        children[179].set(203);
        children[179].set(207);
        children[179].set(221);
        children[179].set(227);
        children[179].set(230);
        children[179].set(234);
        children[179].set(235);
        children[179].set(241);
        children[179].set(242);
        parents[180] = new BitSet();
        parents[180].set(169);
        parents[180].set(240);
        children[180] = new BitSet();
        children[180].set(39);
        children[180].set(92);
        children[180].set(127);
        children[180].set(179);
        parents[181] = new BitSet();
        parents[181].set(200);
        children[181] = new BitSet();
        children[181].set(39);
        children[181].set(63);
        children[181].set(90);
        children[181].set(182);
        children[181].set(212);
        parents[182] = new BitSet();
        parents[182].set(181);
        children[182] = new BitSet();
        children[182].set(134);
        children[182].set(192);
        children[182].set(195);
        children[182].set(212);
        parents[183] = new BitSet();
        parents[183].set(184);
        children[183] = new BitSet();
        children[183].set(42);
        children[183].set(63);
        children[183].set(84);
        parents[184] = new BitSet();
        parents[184].set(200);
        children[184] = new BitSet();
        children[184].set(134);
        children[184].set(183);
        parents[185] = new BitSet();
        parents[185].set(186);
        children[185] = new BitSet();
        children[185].set(2);
        children[185].set(187);
        parents[186] = new BitSet();
        parents[186].set(165);
        parents[186].set(203);
        children[186] = new BitSet();
        children[186].set(69);
        children[186].set(185);
        children[186].set(187);
        parents[187] = new BitSet();
        parents[187].set(185);
        parents[187].set(186);
        children[187] = new BitSet();
        children[187].set(64);
        children[187].set(65);
        parents[188] = new BitSet();
        parents[188].set(224);
        children[188] = new BitSet();
        children[188].set(71);
        children[188].set(87);
        children[188].set(118);
        children[188].set(119);
        children[188].set(169);
        children[188].set(179);
        children[188].set(224);
        children[188].set(243);
        parents[189] = new BitSet();
        parents[189].set(200);
        parents[189].set(211);
        parents[189].set(224);
        children[189] = new BitSet();
        children[189].set(169);
        children[189].set(192);
        parents[190] = new BitSet();
        parents[190].set(179);
        parents[190].set(225);
        children[190] = new BitSet();
        children[190].set(53);
        children[190].set(84);
        children[190].set(92);
        children[190].set(127);
        children[190].set(199);
        children[190].set(216);
        children[190].set(231);
        parents[191] = new BitSet();
        parents[191].set(192);
        children[191] = new BitSet();
        children[191].set(84);
        parents[192] = new BitSet();
        parents[192].set(182);
        parents[192].set(189);
        children[192] = new BitSet();
        children[192].set(70);
        children[192].set(92);
        children[192].set(127);
        children[192].set(176);
        children[192].set(191);
        children[192].set(202);
        children[192].set(212);
        children[192].set(214);
        children[192].set(220);
        children[192].set(249);
        parents[193] = new BitSet();
        parents[193].set(224);
        children[193] = new BitSet();
        children[193].set(62);
        children[193].set(85);
        children[193].set(94);
        children[193].set(169);
        children[193].set(179);
        children[193].set(224);
        children[193].set(243);
        parents[194] = new BitSet();
        parents[194].set(224);
        children[194] = new BitSet();
        children[194].set(58);
        children[194].set(85);
        children[194].set(169);
        children[194].set(171);
        parents[195] = new BitSet();
        parents[195].set(169);
        parents[195].set(182);
        parents[195].set(200);
        parents[195].set(229);
        children[195] = new BitSet();
        children[195].set(88);
        children[195].set(126);
        children[195].set(210);
        parents[196] = new BitSet();
        parents[196].set(197);
        children[196] = new BitSet();
        children[196].set(2);
        children[196].set(222);
        children[196].set(236);
        parents[197] = new BitSet();
        parents[197].set(165);
        parents[197].set(203);
        children[197] = new BitSet();
        children[197].set(21);
        children[197].set(72);
        children[197].set(101);
        children[197].set(196);
        children[197].set(222);
        children[197].set(236);
        parents[198] = new BitSet();
        parents[198].set(233);
        children[198] = new BitSet();
        children[198].set(25);
        children[198].set(35);
        children[198].set(64);
        children[198].set(65);
        children[198].set(76);
        children[198].set(77);
        children[198].set(78);
        children[198].set(79);
        children[198].set(80);
        children[198].set(83);
        children[198].set(149);
        children[198].set(150);
        children[198].set(151);
        children[198].set(152);
        children[198].set(153);
        children[198].set(154);
        parents[199] = new BitSet();
        parents[199].set(190);
        parents[199].set(206);
        children[199] = new BitSet();
        children[199].set(39);
        children[199].set(179);
        parents[200] = new BitSet();
        parents[200].set(174);
        children[200] = new BitSet();
        children[200].set(181);
        children[200].set(184);
        children[200].set(189);
        children[200].set(195);
        children[200].set(211);
        children[200].set(226);
        children[200].set(234);
        children[200].set(239);
        parents[201] = new BitSet();
        parents[201].set(202);
        parents[201].set(213);
        parents[201].set(215);
        children[201] = new BitSet();
        children[201].set(95);
        parents[202] = new BitSet();
        parents[202].set(172);
        parents[202].set(192);
        parents[202].set(226);
        children[202] = new BitSet();
        children[202].set(39);
        children[202].set(89);
        children[202].set(124);
        children[202].set(201);
        parents[203] = new BitSet();
        parents[203].set(179);
        parents[203].set(209);
        parents[203].set(210);
        children[203] = new BitSet();
        children[203].set(26);
        children[203].set(29);
        children[203].set(141);
        children[203].set(165);
        children[203].set(186);
        children[203].set(197);
        parents[204] = new BitSet();
        children[204] = new BitSet();
        children[204].set(37);
        children[204].set(51);
        children[204].set(55);
        parents[205] = new BitSet();
        parents[205].set(224);
        children[205] = new BitSet();
        children[205].set(98);
        children[205].set(169);
        parents[206] = new BitSet();
        parents[206].set(225);
        children[206] = new BitSet();
        children[206].set(20);
        children[206].set(84);
        children[206].set(92);
        children[206].set(127);
        children[206].set(199);
        parents[207] = new BitSet();
        parents[207].set(179);
        parents[207].set(224);
        children[207] = new BitSet();
        children[207].set(13);
        children[207].set(99);
        children[207].set(179);
        children[207].set(208);
        parents[208] = new BitSet();
        parents[208].set(207);
        children[208] = new BitSet();
        children[208].set(39);
        children[208].set(90);
        children[208].set(125);
        children[208].set(175);
        children[208].set(209);
        parents[209] = new BitSet();
        parents[209].set(208);
        children[209] = new BitSet();
        children[209].set(68);
        children[209].set(85);
        children[209].set(100);
        children[209].set(110);
        children[209].set(118);
        children[209].set(169);
        children[209].set(171);
        children[209].set(179);
        children[209].set(203);
        children[209].set(225);
        children[209].set(244);
        parents[210] = new BitSet();
        parents[210].set(195);
        parents[210].set(210);
        parents[210].set(212);
        children[210] = new BitSet();
        children[210].set(39);
        children[210].set(62);
        children[210].set(84);
        children[210].set(92);
        children[210].set(127);
        children[210].set(203);
        children[210].set(210);
        parents[211] = new BitSet();
        parents[211].set(200);
        parents[211].set(211);
        children[211] = new BitSet();
        children[211].set(84);
        children[211].set(90);
        children[211].set(102);
        children[211].set(112);
        children[211].set(125);
        children[211].set(176);
        children[211].set(189);
        children[211].set(211);
        children[211].set(212);
        children[211].set(239);
        parents[212] = new BitSet();
        parents[212].set(172);
        parents[212].set(181);
        parents[212].set(182);
        parents[212].set(192);
        parents[212].set(211);
        parents[212].set(226);
        parents[212].set(229);
        parents[212].set(239);
        children[212] = new BitSet();
        children[212].set(15);
        children[212].set(126);
        children[212].set(210);
        parents[213] = new BitSet();
        parents[213].set(215);
        parents[213].set(228);
        parents[213].set(229);
        children[213] = new BitSet();
        children[213].set(0);
        children[213].set(56);
        children[213].set(104);
        children[213].set(201);
        parents[214] = new BitSet();
        parents[214].set(172);
        parents[214].set(192);
        children[214] = new BitSet();
        children[214].set(0);
        children[214].set(39);
        children[214].set(133);
        children[214].set(215);
        parents[215] = new BitSet();
        parents[215].set(214);
        children[215] = new BitSet();
        children[215].set(0);
        children[215].set(38);
        children[215].set(201);
        children[215].set(213);
        children[215].set(233);
        children[215].set(243);
        parents[216] = new BitSet();
        parents[216].set(178);
        parents[216].set(190);
        parents[216].set(233);
        parents[216].set(237);
        parents[216].set(242);
        children[216] = new BitSet();
        children[216].set(217);
        children[216].set(218);
        parents[217] = new BitSet();
        parents[217].set(216);
        children[217] = new BitSet();
        children[217].set(53);
        children[217].set(84);
        parents[218] = new BitSet();
        parents[218].set(216);
        children[218] = new BitSet();
        children[218].set(53);
        children[218].set(84);
        children[218].set(132);
        children[218].set(144);
        parents[219] = new BitSet();
        parents[219].set(225);
        children[219] = new BitSet();
        children[219].set(123);
        children[219].set(179);
        parents[220] = new BitSet();
        parents[220].set(172);
        parents[220].set(192);
        children[220] = new BitSet();
        children[220].set(1);
        children[220].set(232);
        children[220].set(233);
        parents[221] = new BitSet();
        parents[221].set(179);
        children[221] = new BitSet();
        children[221].set(135);
        children[221].set(136);
        parents[222] = new BitSet();
        parents[222].set(196);
        parents[222].set(197);
        children[222] = new BitSet();
        children[222].set(76);
        children[222].set(77);
        children[222].set(78);
        children[222].set(79);
        children[222].set(80);
        children[222].set(83);
        parents[223] = new BitSet();
        parents[223].set(224);
        children[223] = new BitSet();
        children[223].set(134);
        children[223].set(225);
        parents[224] = new BitSet();
        parents[224].set(169);
        parents[224].set(188);
        parents[224].set(193);
        children[224] = new BitSet();
        children[224].set(169);
        children[224].set(188);
        children[224].set(189);
        children[224].set(193);
        children[224].set(194);
        children[224].set(205);
        children[224].set(207);
        children[224].set(223);
        children[224].set(251);
        parents[225] = new BitSet();
        parents[225].set(175);
        parents[225].set(209);
        parents[225].set(223);
        children[225] = new BitSet();
        children[225].set(167);
        children[225].set(190);
        children[225].set(206);
        children[225].set(219);
        children[225].set(240);
        parents[226] = new BitSet();
        parents[226].set(200);
        children[226] = new BitSet();
        children[226].set(39);
        children[226].set(84);
        children[226].set(90);
        children[226].set(92);
        children[226].set(125);
        children[226].set(127);
        children[226].set(134);
        children[226].set(142);
        children[226].set(176);
        children[226].set(202);
        children[226].set(212);
        children[226].set(229);
        children[226].set(233);
        parents[227] = new BitSet();
        parents[227].set(179);
        children[227] = new BitSet();
        children[227].set(39);
        children[227].set(90);
        children[227].set(125);
        children[227].set(228);
        children[227].set(233);
        parents[228] = new BitSet();
        parents[228].set(227);
        children[228] = new BitSet();
        children[228].set(38);
        children[228].set(49);
        children[228].set(84);
        children[228].set(179);
        children[228].set(213);
        parents[229] = new BitSet();
        parents[229].set(226);
        children[229] = new BitSet();
        children[229].set(38);
        children[229].set(84);
        children[229].set(176);
        children[229].set(195);
        children[229].set(212);
        children[229].set(213);
        children[229].set(233);
        parents[230] = new BitSet();
        parents[230].set(179);
        children[230] = new BitSet();
        children[230].set(39);
        children[230].set(92);
        children[230].set(127);
        children[230].set(179);
        parents[231] = new BitSet();
        parents[231].set(190);
        children[231] = new BitSet();
        children[231].set(2);
        children[231].set(89);
        children[231].set(124);
        children[231].set(233);
        parents[232] = new BitSet();
        parents[232].set(220);
        children[232] = new BitSet();
        children[232].set(39);
        children[232].set(92);
        children[232].set(127);
        children[232].set(233);
        parents[233] = new BitSet();
        parents[233].set(215);
        parents[233].set(220);
        parents[233].set(226);
        parents[233].set(227);
        parents[233].set(229);
        parents[233].set(231);
        parents[233].set(232);
        parents[233].set(240);
        parents[233].set(241);
        parents[233].set(248);
        children[233] = new BitSet();
        children[233].set(84);
        children[233].set(92);
        children[233].set(127);
        children[233].set(198);
        children[233].set(216);
        parents[234] = new BitSet();
        parents[234].set(179);
        parents[234].set(200);
        children[234] = new BitSet();
        children[234].set(157);
        children[234].set(169);
        parents[235] = new BitSet();
        parents[235].set(179);
        children[235] = new BitSet();
        children[235].set(90);
        children[235].set(125);
        children[235].set(157);
        children[235].set(179);
        parents[236] = new BitSet();
        parents[236].set(196);
        parents[236].set(197);
        children[236] = new BitSet();
        children[236].set(149);
        children[236].set(150);
        children[236].set(151);
        children[236].set(152);
        children[236].set(153);
        children[236].set(154);
        parents[237] = new BitSet();
        parents[237].set(239);
        children[237] = new BitSet();
        children[237].set(216);
        children[237].set(238);
        parents[238] = new BitSet();
        parents[238].set(237);
        children[238] = new BitSet();
        children[238].set(2);
        children[238].set(39);
        children[238].set(84);
        children[238].set(90);
        children[238].set(125);
        parents[239] = new BitSet();
        parents[239].set(200);
        parents[239].set(211);
        children[239] = new BitSet();
        children[239].set(112);
        children[239].set(134);
        children[239].set(158);
        children[239].set(212);
        children[239].set(237);
        parents[240] = new BitSet();
        parents[240].set(225);
        children[240] = new BitSet();
        children[240].set(62);
        children[240].set(94);
        children[240].set(166);
        children[240].set(179);
        children[240].set(180);
        children[240].set(233);
        children[240].set(241);
        children[240].set(243);
        children[240].set(245);
        children[240].set(246);
        children[240].set(248);
        parents[241] = new BitSet();
        parents[241].set(179);
        parents[241].set(240);
        children[241] = new BitSet();
        children[241].set(2);
        children[241].set(120);
        children[241].set(121);
        children[241].set(233);
        parents[242] = new BitSet();
        parents[242].set(167);
        parents[242].set(179);
        children[242] = new BitSet();
        children[242].set(0);
        children[242].set(13);
        children[242].set(91);
        children[242].set(126);
        children[242].set(179);
        children[242].set(216);
        children[242].set(247);
        parents[243] = new BitSet();
        parents[243].set(188);
        parents[243].set(193);
        parents[243].set(215);
        parents[243].set(240);
        parents[243].set(244);
        parents[243].set(247);
        parents[243].set(248);
        children[243] = new BitSet();
        children[243].set(84);
        parents[244] = new BitSet();
        parents[244].set(209);
        children[244] = new BitSet();
        children[244].set(39);
        children[244].set(92);
        children[244].set(127);
        children[244].set(155);
        children[244].set(243);
        parents[245] = new BitSet();
        parents[245].set(240);
        children[245] = new BitSet();
        children[245].set(39);
        children[245].set(92);
        children[245].set(127);
        children[245].set(248);
        parents[246] = new BitSet();
        parents[246].set(240);
        children[246] = new BitSet();
        children[246].set(104);
        children[246].set(122);
        parents[247] = new BitSet();
        parents[247].set(242);
        children[247] = new BitSet();
        children[247].set(48);
        children[247].set(243);
        parents[248] = new BitSet();
        parents[248].set(240);
        parents[248].set(245);
        children[248] = new BitSet();
        children[248].set(38);
        children[248].set(155);
        children[248].set(233);
        children[248].set(243);
        parents[249] = new BitSet();
        parents[249].set(192);
        children[249] = new BitSet();
        children[249].set(112);
        children[249].set(250);
        parents[250] = new BitSet();
        parents[250].set(249);
        children[250] = new BitSet();
        children[250].set(42);
        children[250].set(84);
        children[250].set(87);
        children[250].set(92);
        children[250].set(127);
        children[250].set(143);
        parents[251] = new BitSet();
        parents[251].set(224);
        children[251] = new BitSet();
        children[251].set(160);
        children[251].set(169);
        children[251].set(171);
        return IntGraph.create(parents, children);
    }
}
