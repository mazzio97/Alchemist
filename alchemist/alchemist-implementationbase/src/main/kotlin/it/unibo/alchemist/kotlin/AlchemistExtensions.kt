/*
 * Copyright (C) 2010-2019, Danilo Pianini and contributors listed in the main project's alchemist/build.gradle file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */
package it.unibo.alchemist.kotlin

import it.unibo.alchemist.model.implementations.times.DoubleTime
import it.unibo.alchemist.model.interfaces.Time

/**
 * The opposite of [fold].
 *
 * @param extractor A function that provides a sequence of
 * elements given a specific element of the same type.
 * @receiver The starting element to unfold.
 * @return A sequence of [E] generated by unfolding on each
 * element provided by the [extractor] function.
 *
 * @See [fold].
 */
fun <E> E.unfold(extractor: (E) -> Sequence<E>): Sequence<E> =
    sequenceOf(this) + extractor(this).flatMap { it.unfold(extractor) }
operator fun Time.plus(other: Double): Time = plus(DoubleTime(other))
operator fun Time.minus(other: Double): Time = minus(DoubleTime(other))
