package commons;

import io.yooksi.commons.util.MathUtils;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("WeakerAccess")
public class MathUtilsTest {

    @Test
    public void testTruncatingDecimals() {

        // Truncate to 2 decimal places
        Assertions.assertEquals(0.84, MathUtils.truncateDecimals(0.8451, 2));
        Assertions.assertEquals(0.94, MathUtils.truncateDecimals(0.94253, 2));

        // The value should not change here
        Assertions.assertEquals(0.42, MathUtils.truncateDecimals(0.42, 2));
        Assertions.assertEquals(0.1, MathUtils.truncateDecimals(0.1, 1));
        Assertions.assertEquals(1.0, MathUtils.truncateDecimals(1.0, 1));
        Assertions.assertEquals(0.0, MathUtils.truncateDecimals(0.0, 0));
        Assertions.assertEquals(3.253, MathUtils.truncateDecimals(3.253, 3));

        // Zero digits cannot be be appended
        Assertions.assertEquals(1.0000, MathUtils.truncateDecimals(1, 4));
        Assertions.assertEquals(25.0, MathUtils.truncateDecimals(25.0000, 2));

        // Negative values are supported
        Assertions.assertEquals(-1, MathUtils.truncateDecimals(-1, 0));
        Assertions.assertEquals(-1.12, MathUtils.truncateDecimals(-1.12, 2));
        Assertions.assertEquals(-99.8, MathUtils.truncateDecimals(-99.8424, 1));

        // Negative precision values are not allowed
        Assertions.assertThrows(IllegalArgumentException.class, this::truncateDecimalsFail);
    }

    @TestOnly
    /* Should produce an IllegalArgumentException */
    private void truncateDecimalsFail() {
        MathUtils.truncateDecimals(2, -1);
    }
}
