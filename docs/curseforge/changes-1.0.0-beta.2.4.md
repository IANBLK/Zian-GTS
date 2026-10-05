**✨ ZIAN GTS — 1.0.0-beta.2.4**

**📜 UNIFIED COMMAND NAME**

Replaced the public command root with **/ZianGTS**, including its open, status, and recovery sections. Removed **/gtsv2** and updated command help and documentation.

Update existing scripts and GUI buttons to use the new command.

**🔐 LUCKPERMS ON YOUER**

Added detection of LuckPerms installed as a Bukkit plugin on Youer, alongside the existing NeoForge integration.

Explicit permission denials remain effective for operators. Access is denied when an installed permission provider is unavailable.

**💾 SAFER RECOVERY RESOURCE HANDLING**

Journal and history file handles are released when corrupted data prevents opening them, while recovery evidence is preserved.

Existing market data, permission nodes, AVECOINS integration, and transaction recovery remain in place.

**🧪 REGRESSION COVERAGE**

Added regression tests for command registration and the reflective LuckPerms API integration. GitHub Actions successfully built this version.

**📦 UPDATE NOTES**

Install the same **1.0.0-beta.2.4** version on the server and clients.

This release retains the earlier beta improvements to server-side action permissions, AVECOINS 2.3/2.4 wallet compatibility, and the dark-and-gold interface.

**This is a beta release in active development.**
