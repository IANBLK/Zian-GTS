package com.zianblk.ziangts.client

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class ClientSubscriberTest {
    @Test fun `client setup subscriber is an instance method for Kotlin for Forge`() {
        // Inspect bytecode without loading Minecraft client classes in the test JVM.
        val resource = "/com/zianblk/ziangts/client/GtsClientEvents.class"
        val stream = requireNotNull(javaClass.getResourceAsStream(resource))
        var found = false
        stream.use {
            ClassReader(it).accept(object : ClassVisitor(Opcodes.ASM9) {
                override fun visitMethod(access: Int, name: String, descriptor: String,
                                         signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (name == "setup" && descriptor == "(Lnet/neoforged/fml/event/lifecycle/FMLClientSetupEvent;)V") {
                        found = true
                        assertEquals(0, access and Opcodes.ACC_STATIC,
                            "KFF registers the Kotlin object instance; @JvmStatic causes a startup crash")
                    }
                    return null
                }
            }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        }
        assertTrue(found, "Client setup method must be present in the compiled mod")
    }
}
