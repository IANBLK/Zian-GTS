import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;

/** Run with Java 21 and the separately supplied AVECOINS 2.3 JAR on the classpath. */
public class VerifyAvecoinsWallet {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("net.sundggs.avecoins.shop.WalletData");
        var balance = type.getMethod("balance", UUID.class, String.class);
        var credit = type.getMethod("credit", UUID.class, String.class, long.class);
        var debit = type.getMethod("debit", UUID.class, String.class, long.class);
        var copy = type.getMethod("copy");
        var balances = type.getMethod("balances", UUID.class);
        require(type.getField("SLOT_COUNT").getInt(null) == 27, "slot count");
        require(type.getField("STACK_SIZE").getInt(null) == 64, "stack size");
        require(type.getField("MAX_BALANCE").getLong(null) == 1728L, "balance limit");
        UUID player = UUID.randomUUID();
        Object original = type.getConstructor().newInstance();
        Object candidate = copy.invoke(original);
        credit.invoke(candidate, player, "avecoins:goldcoin", 100L);
        require(balance.invoke(original, player, "avecoins:goldcoin").equals(0L), "copy must isolate the original");
        require(debit.invoke(candidate, player, "avecoins:goldcoin", 40L).equals(true), "debit succeeds");
        require(balance.invoke(candidate, player, "avecoins:goldcoin").equals(60L), "exact debit balance");
        require(debit.invoke(candidate, player, "avecoins:goldcoin", 61L).equals(false), "overdraft rejected");
        require(balance.invoke(candidate, player, "avecoins:goldcoin").equals(60L), "overdraft must not mutate");
        ((Map<?, ?>) balances.invoke(candidate, player)).clear();
        require(balance.invoke(candidate, player, "avecoins:goldcoin").equals(60L), "balance map is defensive");
        Object full = type.getConstructor().newInstance();
        credit.invoke(full, player, "avecoins:ironcoin", 65L);
        credit.invoke(full, player, "avecoins:goldcoin", 1600L);
        boolean rejected = false;
        try { credit.invoke(full, player, "avecoins:coppercoin", 1L); }
        catch (InvocationTargetException error) { rejected = error.getCause() instanceof IllegalArgumentException; }
        require(rejected, "27-slot aggregate limit");
        require(balance.invoke(full, player, "avecoins:coppercoin").equals(0L), "rejected credit must not mutate");
        System.out.println("AVECOINS WalletData contract: PASS (copy isolation, debit, overdraft, defensive reads, capacity)");
    }

    private static void require(boolean value, String description) {
        if (!value) throw new AssertionError(description);
    }
}
