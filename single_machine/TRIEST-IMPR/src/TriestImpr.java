import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.Random;

public class TriestImpr extends Triest {

    private Int2ObjectOpenHashMap<IntOpenHashSet> srcToDsts = new Int2ObjectOpenHashMap(); // graph composed of the sampled edges
    private Int2DoubleOpenHashMap nodeToTriangles = new Int2DoubleOpenHashMap(); // local triangle counts
    private double globalTriangle = 0; // global triangle count

    private long s = 0; // number of current samples
    private int nb = 0; // number of uncompensated deletions
    private int ng = 0; // number of uncompensate deletions

    private long findTriangleNum = 0;
    
    int test = 0;

    private final int k; // maximum number of samples
    private final int[][] samples; // sampled edges
    private final Long2IntOpenHashMap edgeToIndex = new Long2IntOpenHashMap(); // edge to the index of cell that the edge is stored in

    private final Random random;
    private final boolean lowerBound;

    /**
     *
     * @param memoryBudget maximum number of samples
     * @param seed random seed
     */
    public TriestImpr(final int memoryBudget, final int seed) {
        this(memoryBudget, seed, true);
    }

    public TriestImpr(final int memoryBudget, final int seed, final boolean lowerBound) {
        random = new Random(seed);
        this.k = memoryBudget;
        samples = new int[2][this.k];
        nodeToTriangles.defaultReturnValue(0);
        this.lowerBound = lowerBound;
    }

    @Override
    public void processAddition(final int src, final int dst) {
        processEdge(src, dst, true);
    }

    @Override
    public void processDeletion(final int src, final int dst) {
        processEdge(src, dst, false);
    }

    public void processEdge(int src, int dst, boolean add) {

        if(src == dst) { //ignore self loop
            return;
        }

		if (src > dst) {
			int temp = src;
			src = dst;
			dst = temp;
		}

        count(src, dst, add); //count the added or deleted triangles

        if(add) {
            //sample edge start
            if(ng + nb == 0) {
                if(edgeToIndex.size() < k) {
                    addEdge(src, dst);
                }
                else if(random.nextDouble() < k / (s + 1.0)) {
                    int index = random.nextInt(edgeToIndex.size());
                    deleteEdge(samples[0][index], samples[1][index]); // remove a random edge from the samples
                    addEdge(src, dst); // store the sampled edge
                }
            }
            else if(random.nextDouble() < nb / (nb + ng + 0.0)){
                addEdge(src, dst); // store the sampled edge
                nb--;
            }
            else {
                ng--;
            }
        } else {

            long key = ((long)src * Integer.MAX_VALUE) + dst;
            if(edgeToIndex.containsKey(key)) {
                deleteEdge(src, dst); // remove the edge from the samples
                nb++;
            }
            else {
                ng++;
            }
        }

        if(add) {
            s++;
        }
        else {
            s--;
        }

        //System.out.println("globalTriangle: " + globalTriangle + ", findTriangleNum: " + findTriangleNum);
        return;
    }

    /**
     * Store a sampled edge
     * @param src source node of the given edge
     * @param dst destination node of the given edge
     */
    private void addEdge(int src, int dst) {

        int sampleNum = edgeToIndex.size();
        samples[0][sampleNum] = src;
        samples[1][sampleNum] = dst;
        long key = ((long)src * Integer.MAX_VALUE) + dst;

        edgeToIndex.put(key, sampleNum);
        if(!srcToDsts.containsKey(src)) {
            srcToDsts.put(src, new IntOpenHashSet());
        }
        srcToDsts.get(src).add(dst);

        if(!srcToDsts.containsKey(dst)) {
            srcToDsts.put(dst, new IntOpenHashSet());
        }
        srcToDsts.get(dst).add(src);
    }

    /**
     * Remove an edge from the samples
     * @param src source node of the given edge
     * @param dst destination node of the given edge
     */
    private void deleteEdge(int src, int dst) {

        int sampleNum = edgeToIndex.size();
        long key = ((long)src * Integer.MAX_VALUE) + dst;
        int index = edgeToIndex.remove(key);

        IntOpenHashSet map = srcToDsts.get(src);
        map.remove(dst);
        if(map.isEmpty()) {
            srcToDsts.remove(src);
        }
        map = srcToDsts.get(dst);
        map.remove(src);
        if(map.isEmpty()) {
            srcToDsts.remove(dst);
        }

        if(index < sampleNum - 1) {
            int newSrc = samples[0][index] = samples[0][sampleNum - 1];
            int newDst = samples[1][index] = samples[1][sampleNum - 1];
            long newKey = ((long) newSrc * Integer.MAX_VALUE) + newDst;
            edgeToIndex.put(newKey, index);
        }
    }

    @Override
    public double getGlobalTriangle() {
        return globalTriangle;
    }

    @Override
    public Int2DoubleOpenHashMap getLocalTriangle() {
        return nodeToTriangles;
    }

    @Override
    public long getFindTriangleNum(){
        return this.findTriangleNum;
    }

    /**
     * counts triangles with the given edge and update estimates
     * @param src the source node of the given edge
     * @param dst the destination node of the given edge
     * @param add true for addition and false for deletion
     */
    private void count(int src, int dst, boolean add) {
        // if this edge has a new node, there cannot be any triangles
        if(!srcToDsts.containsKey(src) || !srcToDsts.containsKey(dst)) {
            return;
        }

        IntOpenHashSet srcSet = srcToDsts.get(src);
        IntOpenHashSet dstSet = srcToDsts.get(dst);

        if(srcSet.size() > dstSet.size()) {
            IntOpenHashSet temp = srcSet;
            srcSet = dstSet;
            dstSet = temp;
        }

        //final double weight = Math.max((s + nb + ng + 0.0) / k * (s + nb + ng - 1.0) / (k - 1.0), 1);
         double weight = Math.max((s + nb + ng + 0.0) / k * (s + nb + ng - 1.0) / (k - 1.0), 1);

        //if(weight != 1){
        //    System.out.println("Weight: " + weight);
        //}

        if(add) { // process the addition
            double count = 0;
            for (int neighbor : srcSet) {
                if (dstSet.contains(neighbor)) {
                    count += 1;
                    nodeToTriangles.addTo(neighbor, weight);

                    //TEST
                    //if(test < 1){
                    //    weight = weight * 10000;
                    //    System.out.println("("+src+", "+dst+", "+neighbor+") s:"+ s +", k:"+k +", nb:"+nb +", ng:" +ng + ", weight:" + weight);
                    //    test++;
                    //}
                }
            }

            if (count > 0) {
                double weightSum = count * weight;
                nodeToTriangles.addTo(src, weightSum);
                nodeToTriangles.addTo(dst, weightSum);
                globalTriangle += weightSum;
                
                findTriangleNum += count;
            }
        }

        else if(lowerBound){ // process the deletion with lower bounding

            double count = 0;
            for (int neighbor : srcSet) {
                if (dstSet.contains(neighbor)) {
                    count += 1;
                    double value = nodeToTriangles.addTo(neighbor, - weight);
                    if(value < weight) {
                        nodeToTriangles.put(neighbor, 0); // lower bounding
                    }
                    //System.out.println("(lowerBound)Delete("+src+", "+dst+", "+neighbor+")");
                }
            }

            if (count > 0) {
                final double weightSum = count * weight;
                double value = nodeToTriangles.addTo(src, - weightSum);
                if(value < weightSum) {
                    nodeToTriangles.put(src, 0); // lower bounding
                }
                value = nodeToTriangles.addTo(dst, - weightSum);
                if(value < weightSum) {
                    nodeToTriangles.put(dst, 0); // lower bounding
                }
                globalTriangle -= weightSum;
                globalTriangle = Math.max(0, globalTriangle); // lower bounding
                
                findTriangleNum -= count;
            }
        }

        else { // process the deletion without lower bounding

            double count = 0;
            for (int neighbor : srcSet) {
                if (dstSet.contains(neighbor)) {
                    count += 1;
                    nodeToTriangles.addTo(neighbor, - weight);

                    System.out.println("Delete("+src+", "+dst+", "+neighbor+")");
                }
            }

            if (count > 0) {
                double weightSum = count * weight;
                nodeToTriangles.addTo(src, - weightSum);
                nodeToTriangles.addTo(dst, - weightSum);
                globalTriangle -= weightSum;
            }
        }
    }
}