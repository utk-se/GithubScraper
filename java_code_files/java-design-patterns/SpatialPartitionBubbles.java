/**
 * The MIT License
 * Copyright (c) 2014-2016 Ilkka Seppälä
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.iluwatar.spatialpartition;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * This class extends the generic SpatialPartition abstract class and is used in
 * our example to keep track of all the bubbles that collide, pop and stay un-popped.
 */

public class SpatialPartitionBubbles extends SpatialPartitionGeneric<Bubble> {

  Hashtable<Integer, Bubble> bubbles;
  QuadTree qTree;

  SpatialPartitionBubbles(Hashtable<Integer, Bubble> bubbles, QuadTree qTree) {
    this.bubbles = bubbles;
    this.qTree = qTree;
  }

  void handleCollisionsUsingQt(Bubble b) {
    //finding points within area of a square drawn with centre same as centre of bubble and length = radius of bubble
    Rect rect = new Rect(b.x, b.y, 2 * b.radius, 2 * b.radius);
    ArrayList<Point> quadTreeQueryResult = new ArrayList<Point>();
    this.qTree.query(rect, quadTreeQueryResult);
    //handling these collisions
    b.handleCollision(quadTreeQueryResult, this.bubbles);
  }
}
