package sqlancer.common.gen;

import java.util.List;

import sqlancer.Randomly;

public abstract class AbstractInsertGenerator<C> {
    //增加差分插入子句sb1
    protected StringBuilder sb = new StringBuilder();
    protected StringBuilder sb1 = new StringBuilder();

    protected void insertColumns(List<C> columns) {
        for (int nrRows = 0; nrRows < Randomly.smallNumber() + 1; nrRows++) {
            if (nrRows != 0) {
                sb.append(", ");
                sb1.append(", ");
            }
            sb.append("(");
            sb1.append("(");
            for (int nrColumn = 0; nrColumn < columns.size(); nrColumn++) {
                if (nrColumn != 0) {
                    sb.append(", ");
                    sb1.append(", ");
                }
                insertValue(columns.get(nrColumn));
            }
            sb.append(")");
            sb1.append(")");
        }
    }

    protected abstract void insertValue(C column);

}
