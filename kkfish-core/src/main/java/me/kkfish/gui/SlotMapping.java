package me.kkfish.gui;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SlotMapping {
    
    public static final int GUI_SIZE_54 = 54;
    public static final int GUI_SIZE_27 = 27;
    public static final int GUI_SIZE_6 = 54;
    
    public static class MainMenu {
        public static final int HOOK_MATERIAL_SLOT = 10;
        public static final int FISH_DEX_SLOT = 12;
        public static final int FISH_RECORD_SLOT = 14;
        public static final int HELP_GUI_SLOT = 16;
        public static final int COMPETITION_SLOT = 22;
        
        public static final Set<Integer> FUNCTIONAL_SLOTS = new HashSet<>(
                Arrays.asList(HOOK_MATERIAL_SLOT, FISH_DEX_SLOT, FISH_RECORD_SLOT, HELP_GUI_SLOT, COMPETITION_SLOT)
        );
        
        public static boolean isFunctionalSlot(int slot) {
            return FUNCTIONAL_SLOTS.contains(slot);
        }
    }
    
    public static class HookMaterial {
        public static final int PREVIOUS_PAGE_SLOT = 48;
        public static final int NEXT_PAGE_SLOT = 50;
        public static final int BACK_BUTTON_SLOT = 45;
        
        public static final int CATEGORY_ALL_SLOT = 36;
        public static final int CATEGORY_BASIC_SLOT = 37;
        public static final int CATEGORY_INTERMEDIATE_SLOT = 38;
        public static final int CATEGORY_ADVANCED_SLOT = 39;
        public static final int CATEGORY_RARE_SLOT = 40;
        public static final int CATEGORY_LEGENDARY_SLOT = 41;
        
        public static final int SORT_NAME_SLOT = 42;
        public static final int SORT_LEVEL_SLOT = 43;
        public static final int SORT_PRICE_SLOT = 44;
        
        public static final int SEARCH_BUTTON_SLOT = 53;
        public static final int CANCEL_SEARCH_SLOT = 52;
        
        public static final int PAGE_INDICATOR_SLOT = 49;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                PREVIOUS_PAGE_SLOT, NEXT_PAGE_SLOT, BACK_BUTTON_SLOT,
                CATEGORY_ALL_SLOT, CATEGORY_BASIC_SLOT, CATEGORY_INTERMEDIATE_SLOT,
                CATEGORY_ADVANCED_SLOT, CATEGORY_RARE_SLOT, CATEGORY_LEGENDARY_SLOT,
                SORT_NAME_SLOT, SORT_LEVEL_SLOT, SORT_PRICE_SLOT,
                SEARCH_BUTTON_SLOT, CANCEL_SEARCH_SLOT, PAGE_INDICATOR_SLOT
        ));
        
        public static final Set<Integer> CATEGORY_SLOTS = new HashSet<>(Arrays.asList(
                CATEGORY_ALL_SLOT, CATEGORY_BASIC_SLOT, CATEGORY_INTERMEDIATE_SLOT,
                CATEGORY_ADVANCED_SLOT, CATEGORY_RARE_SLOT, CATEGORY_LEGENDARY_SLOT
        ));
        
        public static final Set<Integer> SORT_SLOTS = new HashSet<>(Arrays.asList(
                SORT_NAME_SLOT, SORT_LEVEL_SLOT, SORT_PRICE_SLOT
        ));
        
        public static boolean isItemDisplaySlot(int slot) {
            return slot >= 9 && slot < 45 && slot % 9 != 0 && slot % 9 != 8;
        }
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
        
        public static boolean isCategorySlot(int slot) {
            return CATEGORY_SLOTS.contains(slot);
        }
        
        public static boolean isSortSlot(int slot) {
            return SORT_SLOTS.contains(slot);
        }
    }
    
    public static class FishDex {
        public static final int PREVIOUS_PAGE_SLOT = 48;
        public static final int NEXT_PAGE_SLOT = 50;
        public static final int BACK_BUTTON_SLOT = 45;
        
        public static final int PAGE_INDICATOR_SLOT = 49;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                PREVIOUS_PAGE_SLOT, NEXT_PAGE_SLOT, BACK_BUTTON_SLOT, PAGE_INDICATOR_SLOT
        ));
        
        public static boolean isItemDisplaySlot(int slot) {
            return slot >= 9 && slot < 45 && slot % 9 != 0 && slot % 9 != 8;
        }
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
    }
    
    public static class SimpleGUI {
        public static final int BACK_BUTTON_SLOT = 22;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                BACK_BUTTON_SLOT
        ));
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
    }
    
    public static class CompetitionCategory {
        public static final int BACK_BUTTON_SLOT = 22;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                BACK_BUTTON_SLOT
        ));
        
        public static boolean isItemDisplaySlot(int slot) {
            return slot >= 0 && slot < 27 && slot != BACK_BUTTON_SLOT;
        }
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
    }
    
    public static class RewardPreview {
        public static final int BACK_BUTTON_SLOT = 49;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                BACK_BUTTON_SLOT
        ));
        
        public static boolean isItemDisplaySlot(int slot) {
            return slot >= 0 && slot < 54 && slot != BACK_BUTTON_SLOT;
        }
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
    }
    
    public static class SellGUI {
        public static final int BACK_BUTTON_SLOT = 49;
        
        public static final Set<Integer> CONTROL_SLOTS = new HashSet<>(Arrays.asList(
                BACK_BUTTON_SLOT
        ));
        
        public static boolean isItemDisplaySlot(int slot) {
            return slot >= 9 && slot < 45 && slot % 9 != 0 && slot % 9 != 8;
        }
        
        public static boolean isControlSlot(int slot) {
            return CONTROL_SLOTS.contains(slot);
        }
        
        public static boolean isBorderSlot(int slot) {
            return !isItemDisplaySlot(slot);
        }
    }
    
    public static boolean isBorderOrBackgroundSlot(int slot, int guiSize) {
        if (guiSize == GUI_SIZE_54) {
            if (slot < 9 || slot >= 45) {
                return true;
            }
            if (slot % 9 == 0 || slot % 9 == 8) {
                return true;
            }
        }
        else if (guiSize == GUI_SIZE_27) {
            if (slot < 9 || slot >= 18) {
                return true;
            }
            if (slot % 9 == 0 || slot % 9 == 8) {
                return true;
            }
        }
        else if (guiSize == GUI_SIZE_6) {
            return false;
        }
        return false;
    }
    
    public static int getBorderSlotCount(int guiSize) {
        if (guiSize == GUI_SIZE_54) {
            return 32;
        } else if (guiSize == GUI_SIZE_27) {
            return 20;
        } else if (guiSize == GUI_SIZE_6) {
            return 0;
        }
        return 0;
    }
    
    public static int getAvailableSlotCount(int guiSize) {
        return guiSize - getBorderSlotCount(guiSize);
    }
}