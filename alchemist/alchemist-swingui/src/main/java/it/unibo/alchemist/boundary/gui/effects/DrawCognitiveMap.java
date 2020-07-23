/*
 * Copyright (C) 2010-2020, Danilo Pianini and contributors
 * listed in the main project's alchemist/build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.boundary.gui.effects;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import it.unibo.alchemist.boundary.wormhole.interfaces.IWormhole2D;
import it.unibo.alchemist.model.implementations.geometry.euclidean2d.Ellipse;
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition;

import it.unibo.alchemist.model.interfaces.OrientingPedestrian;
import it.unibo.alchemist.model.interfaces.Environment;
import it.unibo.alchemist.model.interfaces.Node;
import it.unibo.alchemist.model.interfaces.Position2D;
import it.unibo.alchemist.model.interfaces.environments.Environment2DWithObstacles;
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.graph.NavigationGraph;
import org.danilopianini.lang.RangedInteger;
import org.danilopianini.view.ExportForGUI;
import org.jgrapht.graph.DefaultEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Draws an orienting pedestrian' cognitive map.
 */
public class DrawCognitiveMap extends DrawOnce {

    /**
     */
    protected static final int MAX_COLOUR_VALUE = 255;
    /**
     */
    protected static final int INITIAL_ALPHA_DIVIDER = 5;
    /**
     */
    protected static final Logger L = LoggerFactory.getLogger(DrawShape.class);
    private static final long serialVersionUID = 1L;
    @ExportForGUI(nameToExport = "A")
    private RangedInteger alpha = new RangedInteger(0, MAX_COLOUR_VALUE, MAX_COLOUR_VALUE / INITIAL_ALPHA_DIVIDER);
    @ExportForGUI(nameToExport = "R")
    private RangedInteger red = new RangedInteger(0, MAX_COLOUR_VALUE, MAX_COLOUR_VALUE);
    @ExportForGUI(nameToExport = "G")
    private RangedInteger green = new RangedInteger(0, MAX_COLOUR_VALUE);
    @ExportForGUI(nameToExport = "B")
    private RangedInteger blue = new RangedInteger(0, MAX_COLOUR_VALUE);
    private Color colorCache = Color.RED;
    @Nullable
    @SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
    private transient NavigationGraph<? extends Euclidean2DPosition, ?, Ellipse, DefaultEdge> cognitiveMap;

    /**
     * @param graphics        graphics
     * @param node        node
     * @param environment      environment
     * @param wormhole the wormhole used to map environment's coords to screen coords
     * @param <T>      concentration type
     * @param <P>      position type
     */
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "unchecked"})
    @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
    @Override
    public <T, P extends Position2D<P>> void apply(
            final Graphics2D graphics,
            final Node<T> node,
            final Environment<T, P> environment,
            final IWormhole2D<P> wormhole
    ) {
        super.apply(graphics, node, environment, wormhole);
        final Integer markerNodeID = getMarkerNodeID();
        if (cognitiveMap == null
                && markerNodeID != null
                && environment.getNodeByID(markerNodeID) instanceof OrientingPedestrian
                && environment instanceof Environment2DWithObstacles
                && environment.makePosition(0.0, 0.0) instanceof Euclidean2DPosition) {
            cognitiveMap = ((OrientingPedestrian) environment.getNodeByID(markerNodeID)).getCognitiveMap();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected <T, P extends Position2D<P>> void draw(
            final Graphics2D g,
            final Node<T> n,
            final Environment<T, P> env,
            final IWormhole2D<P> wormhole
    ) {
        if (cognitiveMap != null) {
            colorCache = new Color(red.getVal(), green.getVal(), blue.getVal(), alpha.getVal());
            g.setColor(Color.RED);
            cognitiveMap.vertexSet().stream()
                    .map(r -> mapEnvEllipseToAwtShape(r, wormhole, env))
                    .forEach(r -> {
                        g.setColor(colorCache);
                        g.fill(r);
                        g.setColor(colorCache.brighter().brighter());
                        g.draw(r);
                    });
            cognitiveMap.vertexSet().forEach(r -> {
                final Point centroidFrom = wormhole.getViewPoint(env.makePosition(r.getCentroid().getX(), r.getCentroid().getY()));
                if (cognitiveMap != null) {
                    cognitiveMap.outgoingEdgesOf(r).forEach(e -> {
                        if (cognitiveMap != null) {
                            final Euclidean2DPosition head = cognitiveMap.getEdgeTarget(e).getCentroid();
                            final Point centroidTo = wormhole.getViewPoint(env.makePosition(head.getX(), head.getY()));
                            g.setColor(colorCache);
                            g.drawLine(centroidFrom.x, centroidFrom.y, centroidTo.x, centroidTo.y);
                        }
                    });
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Color getColorSummary() {
        return colorCache;
    }

    private <P extends Position2D<P>> Shape mapEnvEllipseToAwtShape(
            final Ellipse ellipse,
            final IWormhole2D<P> wormhole,
            final Environment<?, P> environment
    ) {
        final Rectangle2D frame = ellipse.asAwtShape().getFrame();
        final P startEnv = environment.makePosition(frame.getMinX(), frame.getMinY());
        final P endEnv = environment.makePosition(frame.getMaxX(), frame.getMaxY());
        final Point startView = wormhole.getViewPoint(startEnv);
        final Point endView = wormhole.getViewPoint(endEnv);
        final Point2D minPoint = new Point2D.Double(
                Math.min(startView.getX(), endView.getX()),
                Math.min(startView.getY(), endView.getY())
        );
        final Point2D maxPoint = new Point2D.Double(
                Math.max(startView.getX(), endView.getX()),
                Math.max(startView.getY(), endView.getY())
        );
        return new Ellipse2D.Double(
                minPoint.getX(),
                minPoint.getY(),
                maxPoint.getX() - minPoint.getX(),
                maxPoint.getY() - minPoint.getY()
        );
    }

    /**
     * @return alpha channel
     */
    public RangedInteger getAlpha() {
        return alpha;
    }

    /**
     * @param alpha alpha channel
     */
    public void setAlpha(final RangedInteger alpha) {
        this.alpha = alpha;
    }

    /**
     * @return red channel
     */
    public RangedInteger getRed() {
        return red;
    }

    /**
     * @param red red channel
     */
    public void setRed(final RangedInteger red) {
        this.red = red;
    }

    /**
     * @return green channel
     */
    public RangedInteger getGreen() {
        return green;
    }

    /**
     * @param green green channel
     */
    public void setGreen(final RangedInteger green) {
        this.green = green;
    }

    /**
     * @return blue channel
     */
    public RangedInteger getBlue() {
        return blue;
    }

    /**
     * @param blue blue channel
     */
    public void setBlue(final RangedInteger blue) {
        this.blue = blue;
    }
}
