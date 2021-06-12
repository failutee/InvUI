package de.studiocode.inventoryaccess.v1_17_R1.inventory;

import de.studiocode.inventoryaccess.api.abstraction.inventory.AnvilInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_17_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftInventoryAnvil;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

public class AnvilInventoryImpl extends AnvilMenu implements AnvilInventory {
    
    private final String title;
    private final Consumer<String> renameHandler;
    private final CraftInventoryView view;
    private final ServerPlayer player;
    
    private String text;
    private boolean open;
    
    public AnvilInventoryImpl(org.bukkit.entity.Player player, String title, Consumer<String> renameHandler) {
        this(((CraftPlayer) player).getHandle(), title, renameHandler);
    }
    
    public AnvilInventoryImpl(ServerPlayer player, String title, Consumer<String> renameHandler) {
        super(player.nextContainerCounter(), player.getInventory(),
            ContainerLevelAccess.create(player.level, new BlockPos(Integer.MAX_VALUE, 0, 0)));
        
        this.title = title;
        this.renameHandler = renameHandler;
        this.player = player;
        
        CraftInventoryAnvil inventory = new CraftInventoryAnvil(access.getLocation(),
            inputSlots, resultSlots, this);
        this.view = new CraftInventoryView(player.getBukkitEntity(), inventory, this);
    }
    
    public void open() {
        open = true;
        
        // call the InventoryOpenEvent
        CraftEventFactory.callInventoryOpenEvent(player, this);
        
        // set active container
        player.containerMenu = this;
        
        // send open packet
        player.connection.send(new ClientboundOpenScreenPacket(containerId, MenuType.ANVIL, new TextComponent(title)));
        
        // send initial items
        NonNullList<ItemStack> itemsList = NonNullList.of(ItemStack.EMPTY, getItem(0), getItem(1), getItem(2));
        player.connection.send(new ClientboundContainerSetContentPacket(getActiveWindowId(player), itemsList));
        
        // init menu
        player.initMenu(this);
    }
    
    public void sendItem(int slot) {
        player.connection.send(new ClientboundContainerSetSlotPacket(getActiveWindowId(player), slot, getItem(slot)));
    }
    
    public void setItem(int slot, ItemStack item) {
        if (slot < 2) inputSlots.setItem(slot, item);
        else resultSlots.setItem(0, item);
        
        if (open) sendItem(slot);
    }
    
    private ItemStack getItem(int slot) {
        if (slot < 2) return inputSlots.getItem(slot);
        else return resultSlots.getItem(0);
    }
    
    private int getActiveWindowId(ServerPlayer player) {
        AbstractContainerMenu container = player.containerMenu;
        return container == null ? -1 : container.containerId;
    }
    
    @Override
    public void setItem(int slot, org.bukkit.inventory.ItemStack itemStack) {
        setItem(slot, CraftItemStack.asNMSCopy(itemStack));
    }
    
    @Override
    public Inventory getBukkitInventory() {
        return view.getTopInventory();
    }
    
    @Override
    public String getRenameText() {
        return text;
    }
    
    @Override
    public boolean isOpen() {
        return open;
    }
    
    // --- ContainerAnvil ---
    
    @Override
    public CraftInventoryView getBukkitView() {
        return view;
    }
    
    /**
     * Called every tick to see if the {@link Player} can still use that container.
     * (Used to for checking the distance between the {@link Player} and the container
     * and closing the window when the distance gets too big.)
     *
     * @param player The {@link Player}
     * @return If the {@link Player} can still use that container
     */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
    
    /**
     * Called when the rename text gets changed.
     *
     * @param s The new rename text
     */
    @Override
    public void setItemName(String s) {
        // save rename text
        text = s;
        
        // call the rename handler
        if (renameHandler != null) renameHandler.accept(s);
        
        // the client expects the item to change to it's new name and removes it from the inventory, so it needs to be sent again
        sendItem(2);
    }
    
    /**
     * Called when the container is closed to give the items back.
     *
     * @param player The {@link Player} that closed this container
     */
    @Override
    public void removed(Player player) {
        open = false;
    }
    
    
    /**
     * Called when the container gets closed to put items back into a players
     * inventory or drop them in the world.
     *
     * @param player    The {@link Player} that closed this container
     * @param container The container
     */
    @Override
    protected void clearContainer(Player player, Container container) {
        open = false;
    }
    
    /**
     * Called when both items in the {@link AnvilMenu#inputSlots} were set to create
     * the resulting product, calculate the level cost and call the {@link PrepareAnvilEvent}.
     */
    @Override
    public void createResult() {
        // empty
    }
    
    
}
