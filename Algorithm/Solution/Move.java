// Author: Othmane

package Algorithm.Solution;

/**
 * A pair of indices that applies elementary in-place transformations to an
 * integer sequence: right shift, left shift, swap and segment reversal
 * (2-opt). These are the low-level array operations underlying the local
 * search moves.
 *
 * @author Othmane EL YAAKOUBI
 */
public class Move {
    private int index1, index2;

//    void display(){
//        System.out.println("( " + this.index1 + " , " + this.index2 + " )");
//    }

    /**
     * @param a the first index
     * @param b the second index
     */
    public Move(int a, int b) {
        this.index1 = a;
        this.index2 = b;
    }

//    boolean EqualsTo(Move m) {
//        return (this.index1 == m.index2 && this.index2 == m.index1) || (this.index1 == m.index1 && this.index2 == m.index2);
//    }

    /**
     * Moves the element at {@code index2} to {@code index1}, shifting the
     * elements in between one position to the right.
     *
     * @param sequence the sequence to modify in place
     */
    public void rightShift(int[] sequence) {
        if (this.index1 < this.index2) {
            int aux = sequence[this.index2];
            for (int k = this.index2; k > this.index1;)
                sequence[k] = sequence[--k];
            sequence[this.index1] = aux;
        }
    }
    
    /**
     * Moves the element at {@code index1} to {@code index2}, shifting the
     * elements in between one position to the left.
     *
     * @param array the sequence to modify in place
     */
    public void leftShift(int[] array){
        if(this.index1 < this.index2){
            int aux = array[this.index1];
            for(int k = this.index1; k < this.index2;)
                array[k] = array[++k];
            array[this.index2] = aux;
        }
    }

    /**
     * Swaps the elements at {@code index1} and {@code index2}.
     *
     * @param array the sequence to modify in place
     */
    public void swap(int[] array){
       int aux = array[this.index1];
       array[this.index1] = array[this.index2];
       array[this.index2] = aux;
    }

    /**
     * Reverses the segment between {@code index1} and {@code index2} inclusive
     * (the 2-opt array operation).
     *
     * @param array the sequence to modify in place
     */
    public void _2Opt(int[] array) {
        if (this.index1 < this.index2) {
            for (int k = this.index1, l = this.index2; k < l; k++, l--)
                new Move(k, l).swap(array);
        }
    }
}