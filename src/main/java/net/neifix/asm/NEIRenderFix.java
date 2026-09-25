package net.neifix.asm;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import com.google.common.eventbus.EventBus;

import java.util.*;

@IFMLLoadingPlugin.SortingIndex(Integer.MAX_VALUE)
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("NEIRenderFix")
public class NEIRenderFix implements IFMLLoadingPlugin {
    private static final Logger logger = LogManager.getLogger("NEIRenderFix");

    public NEIRenderFix() {
        logger.info("[NEIRenderFix] CoreMod initialized - Mobile OpenGL render fix active");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{
            "net.neifix.asm.NEIRenderFixTransformerImpl",
            "net.neifix.asm.ColorResourceTransformerImpl"
        };
    }

    @Override
    public String getModContainerClass() {
        return "net.neifix.asm.NEIRenderFixContainerImpl";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

class NEIRenderFixTransformerImpl implements IClassTransformer {
    private static final Logger logger = LogManager.getLogger("NEIRenderFixTransformer");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!transformedName.startsWith("codechicken.nei")) {
            return basicClass;
        }

        try {
            if (transformedName.equals("codechicken.nei.recipe.GuiRecipe")) {
                return transformGuiRecipe(basicClass);
            } else if (transformedName.equals("codechicken.nei.WorldOverlayRenderer")) {
                return transformWorldOverlayRenderer(basicClass);
            } else if (transformedName.equals("codechicken.nei.SubsetWidget")) {
                return transformSubsetWidget(basicClass);
            } else if (transformedName.equals("codechicken.nei.HUDRenderer")) {
                return transformHUDRenderer(basicClass);
            }
        } catch (Exception e) {
            logger.error("[NEIRenderFix] Failed to transform " + transformedName, e);
            return basicClass;
        }

        return basicClass;
    }

    private byte[] transformGuiRecipe(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.contains("draw")) {
                wrapWithGLState(method);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] transformWorldOverlayRenderer(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.contains("render")) {
                wrapWithGLState(method);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] transformSubsetWidget(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            replaceColorResourceCalls(method);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private byte[] transformHUDRenderer(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (MethodNode method : cn.methods) {
            if (method.name.contains("render")) {
                wrapWithGLState(method);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private void wrapWithGLState(MethodNode method) {
        InsnList instructions = method.instructions;
        
        InsnList prepend = new InsnList();
        prepend.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "org/lwjgl/opengl/GL11",
            "glPushMatrix",
            "()V",
            false
        ));
        instructions.insert(prepend);

        InsnList append = new InsnList();
        append.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "org/lwjgl/opengl/GL11",
            "glPopMatrix",
            "()V",
            false
        ));
        instructions.add(append);
    }

    private void replaceColorResourceCalls(MethodNode method) {
        InsnList instructions = method.instructions;
        AbstractInsnNode[] nodes = instructions.toArray();

        for (AbstractInsnNode node : nodes) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) node;
                if (min.owner.equals("com/gtnewhorizon/gtnhlib/color/ColorResource")) {
                    min.owner = "net/neifix/asm/ColorResourceCompatImpl";
                }
            }
        }
    }
}

class ColorResourceTransformerImpl implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!transformedName.startsWith("codechicken.nei")) {
            return basicClass;
        }

        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean modified = false;

        for (MethodNode method : cn.methods) {
            modified |= transformMethod(method);
        }

        if (modified) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return basicClass;
    }

    private boolean transformMethod(MethodNode method) {
        boolean modified = false;
        InsnList instructions = method.instructions;
        AbstractInsnNode[] nodes = instructions.toArray();

        for (AbstractInsnNode node : nodes) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) node;

                if (min.owner.equals("com/gtnewhorizon/gtnhlib/color/ColorResource")) {
                    min.owner = "net/neifix/asm/ColorResourceCompatImpl";
                    modified = true;
                }
            }
        }

        return modified;
    }
}

class ColorResourceCompatImpl {
    private static final Map<String, int[]> colorCache = new HashMap<>();
    
    static {
        colorCache.put("button_background", new int[]{72, 72, 72, 255});
        colorCache.put("button_hover", new int[]{100, 100, 100, 255});
        colorCache.put("button_text", new int[]{255, 255, 255, 255});
        colorCache.put("panel_background", new int[]{50, 50, 50, 200});
        colorCache.put("text_default", new int[]{255, 255, 255, 255});
    }

    public static int[] getColor(String resourceName) {
        return colorCache.getOrDefault(resourceName, new int[]{255, 255, 255, 255});
    }

    public static void applyColor(String resourceName) {
        int[] color = getColor(resourceName);
        GL11.glColor4f((color[0] & 0xFF) / 255.0f, (color[1] & 0xFF) / 255.0f, (color[2] & 0xFF) / 255.0f, (color[3] & 0xFF) / 255.0f);
    }

    public static void applyColor(String resourceName, float alpha) {
        int[] color = getColor(resourceName);
        GL11.glColor4f((color[0] & 0xFF) / 255.0f, (color[1] & 0xFF) / 255.0f, (color[2] & 0xFF) / 255.0f, alpha);
    }
}

class NEIRenderFixContainerImpl extends DummyModContainer {
    public NEIRenderFixContainerImpl() {
        super(new ModMetadata());
        ModMetadata meta = getMetadata();
        meta.modId = "neirenderfix";
        meta.name = "NEI Render Fix Mobile";
        meta.version = "1.0.0";
        meta.description = "Fixes OpenGL render crashes on mobile FCL";
        meta.authorList = Arrays.asList("Mobile Fixer");
        meta.useDependencyInformation = true;
    }

    @Override
    public boolean registerBus(EventBus bus, cpw.mods.fml.common.LoaderState state) {
        return true;
    }
          }
