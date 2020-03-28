package it.unibo.alchemist.model.implementations.reactions

import it.unibo.alchemist.model.implementations.actions.Seek
import it.unibo.alchemist.model.implementations.actions.Seek2D
import it.unibo.alchemist.model.implementations.geometry.closestPointTo
import it.unibo.alchemist.model.implementations.geometry.intersectionLines
import it.unibo.alchemist.model.implementations.geometry.intersectsBoundaryExcluded
import it.unibo.alchemist.model.implementations.geometry.midPoint
import it.unibo.alchemist.model.implementations.geometry.toVector
import it.unibo.alchemist.model.implementations.geometry.resize
import it.unibo.alchemist.model.implementations.geometry.contains
import it.unibo.alchemist.model.implementations.geometry.magnitude
import it.unibo.alchemist.model.implementations.geometry.intersection
import it.unibo.alchemist.model.implementations.geometry.SegmentsIntersectionTypes
import it.unibo.alchemist.model.implementations.geometry.angleBetween
import it.unibo.alchemist.model.implementations.positions.Euclidean2DPosition
import it.unibo.alchemist.model.interfaces.graph.GraphEdge
import it.unibo.alchemist.model.interfaces.graph.NavigationGraph
import it.unibo.alchemist.model.implementations.graph.builder.GraphBuilder
import it.unibo.alchemist.model.implementations.graph.builder.addEdge
import it.unibo.alchemist.model.implementations.graph.builder.addUndirectedEdge
import it.unibo.alchemist.model.implementations.graph.dijkstraShortestPath
import it.unibo.alchemist.model.interfaces.graph.GraphEdgeWithData
import it.unibo.alchemist.model.implementations.graph.Euclidean2DCrossing
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.OrientingPedestrian
import it.unibo.alchemist.model.interfaces.Pedestrian
import it.unibo.alchemist.model.interfaces.TimeDistribution
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.Time
import it.unibo.alchemist.model.interfaces.Reaction
import it.unibo.alchemist.model.interfaces.geometry.euclidean.twod.ConvexEuclidean2DShape
import it.unibo.alchemist.model.interfaces.geometry.euclidean.twod.ConvexPolygon
import it.unibo.alchemist.model.interfaces.geometry.euclidean.twod.Euclidean2DSegment
import it.unibo.alchemist.model.interfaces.geometry.euclidean.twod.Euclidean2DTransformation
import java.awt.Shape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * An [AbstractOrientingBehavior] in an euclidean bidimensional space.
 * Accepts an [environmentGraph] whose nodes are [ConvexPolygon]s and edges store
 * information regarding the shape of each passage. In particular, edges of
 * the [environmentGraph] are [GraphEdgeWithData] storing an [Euclidean2DSegment].
 * Similarly to an [Euclidean2DCrossing], given an edge e connecting convex
 * polygon a to convex polygon b, the segment provided by e MUST belong to
 * the boundary of a, but can or cannot belong the boundary of b.
 * Additionally, this class redefines [moveTowards] in order to perform a more
 * sophisticated movement, see [Seek2D].
 *
 * @param T the concentration type.
 * @param N the type of landmarks of the pedestrian's cognitive map.
 * @param E the type of edges of the pedestrian's cognitive map.
 * @param M the type of nodes of the [environmentGraph].
 * @param F the type of edges of the [environmentGraph].
 */
open class OrientingBehavior2D<
    T,
    N : ConvexEuclidean2DShape,
    E : GraphEdge<N>,
    M : ConvexPolygon,
    F : GraphEdgeWithData<M, Euclidean2DSegment>
>(
    environment: Environment<T, Euclidean2DPosition>,
    pedestrian: OrientingPedestrian<T, Euclidean2DPosition, Euclidean2DTransformation, N, E>,
    timeDistribution: TimeDistribution<T>,
    environmentGraph: NavigationGraph<Euclidean2DPosition, Euclidean2DTransformation, M, F>
) : AbstractOrientingBehavior<
        T,
        Euclidean2DPosition,
        Euclidean2DTransformation,
        N,
        E,
        M,
        F
    >(environment, pedestrian, timeDistribution, environmentGraph) {

    override fun moveTowards(target: Euclidean2DPosition, currentRoom: M?, targetEdge: F) {
        if (currentRoom == null) {
            Seek2D(environment, this, pedestrian, *target.cartesianCoordinates).execute()
        } else {
            val currPos = environment.getPosition(pedestrian)
            /*
             * Sophisticated seek is used to find the desired movement of the pedestrian.
             */
            val movement = Seek2D(environment, this, pedestrian, *target.cartesianCoordinates).nextPosition
            var nextPos = currPos + movement
            /*
             * If such movement leads outside the current room and not through the desired
             * edge, it is corrected.
             */
            if (!currentRoom.contains(nextPos) && !crosses(currPos, nextPos, targetEdge)) {
                nextPos = adjustMovement(currPos, nextPos, currentRoom)
            }
            /*
             * Normal seek is used to actually move, this because sophisticated seek may anyway
             * bring the pedestrian outside the current room crossing an edge which is not the
             * target one and we don't want it.
             */
            Seek(environment, this, pedestrian, *nextPos.cartesianCoordinates).execute()
        }
    }

    /*
     * We build a graph composed by the edges' midpoints, the room vertices and the provided
     * destination, then we compute the shortest paths between each midpoint and the final
     * destination and rank each edge consequently.
     */
    override fun computeEdgeRankings(currentRoom: M, destination: Euclidean2DPosition): Map<F, Int> {
        val builder = GraphBuilder<Euclidean2DPosition, GraphEdge<Euclidean2DPosition>>()
        /*
         * Maps each edge's midpoint to the correspondent edge object
         */
        val edges = environmentGraph.edgesFrom(currentRoom).map { it.data.midPoint() to it }
        currentRoom.vertices().indices
            .map { currentRoom.getEdge(it) }
            .forEach { side ->
                /*
                 * The midpoints of the passages lying on the side
                 * of the current room being considered
                 */
                val doorCenters = edges.map { it.first }
                    .filter { side.contains(it) }
                    .sortedBy { it.getDistanceTo(side.first) }
                    .toTypedArray()
                mutableListOf(side.first, *doorCenters, side.second)
                    .zipWithNext()
                    .forEach { builder.addUndirectedEdge(it.first, it.second) }
            }
        builder.nodes().forEach {
            if (it != destination && !currentRoom.intersectsBoundaryExcluded(Pair(it, destination))) {
                builder.addEdge(it, destination)
            }
        }
        val graph = builder.build()
        val sorted = edges
            .sortedBy { (midPoint, _) ->
                graph.dijkstraShortestPath(midPoint, destination, { e -> e.tail.getDistanceTo(e.head) })?.weight
            }
            .map { it.second }
        return environmentGraph.edgesFrom(currentRoom).map { it to sorted.indexOf(it) + 1 }.toMap()
    }

    /*
     * The passage is represented as an [Euclidean2DSegment] belonging to the
     * boundary of the current room. This method finds the point of such
     * segment which is more convenient to cross.
     */
    override fun computeSubdestination(targetEdge: F): Euclidean2DPosition {
        with(targetEdge.data) {
            /*
             * The ideal movement the pedestrian would perform connects its current
             * position to the centroid of the next room.
             */
            val movement = Pair(targetEdge.head.centroid, environment.getPosition(pedestrian))
            /*
             * The sub-destination is computed as the point belonging to the crossing
             * which is closest to the intersection between the lines defined by movement
             * and the crossing itself.
             */
            val subdestination = closestPointTo(intersectionLines(this, movement).point.get())
            /*
             * If the sub-destination is an end point of the crossing, we move it slightly
             * away from such end point to avoid behaviors in which the pedestrian always
             * moves remaining attached to the walls.
             */
            if (subdestination == first || subdestination == second) {
                val correction = if (subdestination == first) {
                    second - first
                } else {
                    first - second
                }.resize(toVector().magnitude() * 0.2)
                return subdestination + correction
            }
            return subdestination
        }
    }

    /**
     * We add a factor taking into account the congestion of the room the edge
     * being weighted leads to.
     */
    public override fun weight(edge: F, rank: Int?): Double = super.weight(edge, rank) * congestionFactor(edge.head)

    private fun congestionFactor(room: M): Double =
        environment.nodes
            .filterIsInstance<Pedestrian<T>>()
            .filter { room.contains(environment.getPosition(it)) }
            .count()
            .let { (pedestrian.shape.diameter.pow(2) * it / room.asAwtShape().area()) + 1 }

    /**
     */
    @Suppress("UNCHECKED_CAST")
    override fun cloneOnNewNode(n: Node<T>?, currentTime: Time?): Reaction<T> {
        require(n as? OrientingPedestrian<T, Euclidean2DPosition, Euclidean2DTransformation, M, F> != null) {
            "node not compatible, required: " + pedestrian.javaClass + ", found: " + n?.javaClass
        }
        n as OrientingPedestrian<T, Euclidean2DPosition, Euclidean2DTransformation, M, F>
        return OrientingBehavior2D(environment, n, timeDistribution, environmentGraph)
    }

    /*
     * If nextPos is outside the current room, it is corrected to be contained in such area.
     * In particular, a new nextPos is found, whose distance from currPos is EQUAL to the
     * distance of the old nextPos from currPos. That is to say, the magnitude of the
     * pedestrian's movement isn't reduced.
     */
    private fun adjustMovement(currPos: Euclidean2DPosition, nextPos: Euclidean2DPosition, currRoom: M): Euclidean2DPosition =
        currRoom.vertices().indices
            .map { currRoom.getEdge(it) }
            .filter {
                intersection(currPos to nextPos, it).type == SegmentsIntersectionTypes.POINT
            }
            .map {
                intersection(it, currPos, nextPos.getDistanceTo(currPos))
            }
            .flatMap { mutableListOf(it.point1, it.point2) }
            .filter { it.isPresent }
            .map { it.get() }
            .filter { (it - currPos).angleBetween(nextPos - currPos) <= PI / 2 }
            .minBy { it.getDistanceTo(nextPos) } ?: nextPos

    /*
     * Checks whether the segment described by (currPos, nextPos) intersects the given edge.
     */
    private fun crosses(currPos: Euclidean2DPosition, nextPos: Euclidean2DPosition, targetEdge: F): Boolean =
        intersection(Pair(currPos, nextPos), targetEdge.data).type == SegmentsIntersectionTypes.POINT

    /*
     * A rough estimation of the area of a shape.
     */
    private fun Shape.area(): Double =
        with(bounds2D) {
            abs(width * height)
        }
}