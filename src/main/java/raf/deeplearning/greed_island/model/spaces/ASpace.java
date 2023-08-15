package raf.deeplearning.greed_island.model.spaces;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public abstract class ASpace {

    private int x,y,z;
    private SpaceType type;
    private boolean reachable;


    public abstract char getSpaceSymbol();

    @Override
    public String toString() {
        return "ASpace{" +
                "x=" + x +
                ", y=" + y +
                ", z=" + z +
                ", type=" + type +
                ", reachable=" + reachable +
                '}';
    }
}
