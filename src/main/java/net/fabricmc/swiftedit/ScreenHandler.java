package net.fabricmc.swiftedit;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ScreenHandler extends Screen {
    public ScreenHandler() {
        super(Text.of("Info Screen Title"));
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        drawCenteredText(matrices, textRenderer, new LiteralText("Hello, Fabric!").formatted(Formatting.WHITE), width / 2, height / 2 - 10, 0xFFFFFF);
        super.render(matrices, mouseX, mouseY, delta);
    }
}

