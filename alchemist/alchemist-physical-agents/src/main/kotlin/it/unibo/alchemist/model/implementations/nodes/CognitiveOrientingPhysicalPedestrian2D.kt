/*
 * Copyright (C) 2010-2020, Danilo Pianini and contributors
 * listed in the main project's alchemist/build.gradle.kts file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.model.implementations.nodes

import it.unibo.alchemist.model.implementations.geometry.euclidean2d.Ellipse
import it.unibo.alchemist.model.interfaces.Molecule
import it.unibo.alchemist.model.interfaces.OrientingPedestrian2D
import it.unibo.alchemist.model.interfaces.PedestrianGroup2D
import it.unibo.alchemist.model.interfaces.environments.EuclideanPhysics2DEnvironmentWithGraph
import it.unibo.alchemist.model.interfaces.geometry.euclidean2d.ConvexPolygon
import org.apache.commons.math3.random.RandomGenerator
import org.jgrapht.graph.DefaultEdge

/**
 * A cognitive [OrientingPedestrian2D] capable of physical interactions.
 * TODO(rename it into something linke "SmartPedestrian2D"?)
 */
class CognitiveOrientingPhysicalPedestrian2D<T, N : ConvexPolygon, E> @JvmOverloads constructor(
    environment: EuclideanPhysics2DEnvironmentWithGraph<*, T, N, E>,
    randomGenerator: RandomGenerator,
    override val knowledgeDegree: Double,
    group: PedestrianGroup2D<T>? = null,
    age: String,
    gender: String,
    danger: Molecule? = null
) : CognitivePhysicalPedestrian2D<T>(
    environment,
    randomGenerator,
    age,
    gender,
    danger,
    group
), OrientingPedestrian2D<T, Ellipse, DefaultEdge> {

    /**
     * The orienting part of the pedestrian.
     */
    private val orienting = HomogeneousOrientingPedestrian2D(environment, randomGenerator, knowledgeDegree)

    override val cognitiveMap get() = orienting.cognitiveMap

    override val volatileMemory get() = orienting.volatileMemory
}
