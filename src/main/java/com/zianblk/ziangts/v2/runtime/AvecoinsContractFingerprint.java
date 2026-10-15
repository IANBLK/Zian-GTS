package com.zianblk.ziangts.v2.runtime;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Accept later version numbers only when every reviewed wallet class is byte-identical.
 * Reflection checks in the caller still validate the live method and capacity contract.
 */
public final class AvecoinsContractFingerprint {
    private AvecoinsContractFingerprint() {}
    private static final Map<String,String> INSPECTED = Map.ofEntries(
        Map.entry("net.sundggs.avecoins.config.CraftingConfig","dc3870ff21445838d5edce955d5757677f5e8abc4a7a7cf9bccc27889b242244"),
        Map.entry("net.sundggs.avecoins.shop.WalletStore","1ad01dba22752bd4de4a1eae96905b094be29c2501f2b628f4dc63701eae0b0e"),
        Map.entry("net.sundggs.avecoins.shop.WalletData","a3373a9de3ff9d8a3fdad9a032b6791c938bb4bc6cb9b283a81d869f586b8b81")
    );
    public static boolean matchesInspectedContract() {
        try {
            for (var entry : INSPECTED.entrySet()) {
                Class<?> type = Class.forName(entry.getKey());
                String resource = "/" + entry.getKey().replace('.', '/') + ".class";
                try (InputStream in = type.getResourceAsStream(resource)) {
                    if (in == null) return false;
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
                    if (!HexFormat.of().formatHex(digest.digest()).equals(entry.getValue())) return false;
                }
            }
            return true;
        } catch (Exception | LinkageError error) { return false; }
    }
}
