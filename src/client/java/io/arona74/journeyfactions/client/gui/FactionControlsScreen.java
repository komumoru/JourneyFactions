package io.arona74.journeyfactions.client.gui;

import io.arona74.journeyfactions.JourneyFactions;
import io.arona74.journeyfactions.data.ClientFaction;
import io.arona74.journeyfactions.journeymap.FactionDisplayManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

/**
 * Custom GUI screen for faction controls
 * Based on MapFrontiers approach for JourneyMap integration
 */
public class FactionControlsScreen extends Screen {
    
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    
    private ButtonWidget toggleButton;
    private ButtonWidget statusButton;
    private ButtonWidget closeButton;
    
    private int factionCount = 0;
    private int totalChunks = 0;
    
    public FactionControlsScreen() {
        super(Text.literal("Faction Controls"));
        updateFactionStats();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        
        // Toggle display button
        this.toggleButton = ButtonWidget.builder(
            getToggleButtonText(),
            button -> toggleFactionDisplay()
        ).dimensions(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        
        // Close button
        this.closeButton = ButtonWidget.builder(
            Text.literal("Close"),
            button -> this.close()
        ).dimensions(centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        
        this.addDrawableChild(this.toggleButton);
        this.addDrawableChild(this.statusButton);
        this.addDrawableChild(this.closeButton);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        this.renderBackground(context);
        
        // Draw title
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.title,
            this.width / 2,
            20,
            0xFFFFFF
        );
        
        // Draw faction information
        int infoY = 50;
        String statusLine = String.format("Factions: %d | Claimed Chunks: %d", factionCount, totalChunks);
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(statusLine),
            this.width / 2,
            infoY,
            0xCCCCCC
        );
        
        // Draw current display status
        String displayStatus = FactionDisplayManager.isFactionDisplayEnabled() ? "Visible" : "Hidden";
        Formatting statusColor = FactionDisplayManager.isFactionDisplayEnabled() ? Formatting.GREEN : Formatting.RED;
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Faction Territories: ").append(Text.literal(displayStatus).formatted(statusColor)),
            this.width / 2,
            infoY + 15,
            0xCCCCCC
        );
        
        // Draw keybinding hint
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal("Tip: Press 'G' key to toggle quickly").formatted(Formatting.GRAY),
            this.width / 2,
            this.height - 30,
            0x888888
        );
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private Text getToggleButtonText() {
        if (FactionDisplayManager.isFactionDisplayEnabled()) {
            return Text.literal("Hide Faction Territories").formatted(Formatting.RED);
        } else {
            return Text.literal("Show Faction Territories").formatted(Formatting.GREEN);
        }
    }
    
    private void toggleFactionDisplay() {
        FactionDisplayManager.toggleFactionDisplay();
        
        // Update button text
        this.toggleButton.setMessage(getToggleButtonText());
        
        // Show feedback message
        if (this.client != null && this.client.player != null) {
            String message = FactionDisplayManager.isFactionDisplayEnabled() 
                ? "§eFaction territories: §aShown" 
                : "§eFaction territories: §cHidden";
            
            this.client.player.sendMessage(Text.literal(message), true);
        }
        
        // JourneyFactions.LOGGER.info("Faction display toggled via GUI to: {}", 
        //    FactionDisplayManager.isFactionDisplayEnabled() ? "ENABLED" : "DISABLED");
    }
    
    private void updateFactionStats() {
        try {
            Collection<ClientFaction> factions = JourneyFactions.getFactionManager().getAllFactions();
            factionCount = 0;
            totalChunks = 0;
            
            for (ClientFaction faction : factions) {
                if (faction.getType() == ClientFaction.FactionType.PLAYER) {
                    factionCount++;
                }
                totalChunks += faction.getClaimedChunks().size();
            }
        } catch (Exception e) {
            // JourneyFactions.LOGGER.warn("Error updating faction stats: {}", e.getMessage());
        }
    }
    
    @Override
    public void close() {
        super.close();
        // JourneyFactions.LOGGER.debug("Faction controls screen closed");
    }
    
    @Override
    public boolean shouldPause() {
        return false; // Don't pause the game when this screen is open
    }
}