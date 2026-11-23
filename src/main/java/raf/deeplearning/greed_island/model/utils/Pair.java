package raf.deeplearning.greed_island.model.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class Pair<I extends Number, I1 extends Number> {
    int x1,x2;
}
