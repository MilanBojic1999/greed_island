package raf.deeplearning.greed_island.model.spaces.factory;

import raf.deeplearning.greed_island.model.spaces.ASpace;
import raf.deeplearning.greed_island.model.spaces.Elevation;
import raf.deeplearning.greed_island.model.spaces.Wood;

public class WoodFactory implements ISpaceFactory{
    @Override
    public ASpace crateSpace(int x, int y, int z) {
        return new Wood(x,y,z);
    }
}
