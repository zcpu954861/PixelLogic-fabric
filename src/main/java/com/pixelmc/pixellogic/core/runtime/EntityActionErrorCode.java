package com.pixelmc.pixellogic.core.runtime;

import com.google.gson.annotations.SerializedName;

public enum EntityActionErrorCode {
    @SerializedName("entity_action_invalid_number")
    ENTITY_ACTION_INVALID_NUMBER("entity_action_invalid_number"),
    @SerializedName("entity_damage_kind_unsupported")
    ENTITY_DAMAGE_KIND_UNSUPPORTED("entity_damage_kind_unsupported"),
    @SerializedName("entity_damage_rejected")
    ENTITY_DAMAGE_REJECTED("entity_damage_rejected"),
    @SerializedName("entity_health_above_maximum")
    ENTITY_HEALTH_ABOVE_MAXIMUM("entity_health_above_maximum"),
    @SerializedName("entity_kill_rejected")
    ENTITY_KILL_REJECTED("entity_kill_rejected"),
    @SerializedName("entity_remove_player_forbidden")
    ENTITY_REMOVE_PLAYER_FORBIDDEN("entity_remove_player_forbidden"),
    @SerializedName("status_effect_unknown")
    STATUS_EFFECT_UNKNOWN("status_effect_unknown"),
    @SerializedName("status_effect_rejected")
    STATUS_EFFECT_REJECTED("status_effect_rejected"),
    @SerializedName("player_game_mode_rejected")
    PLAYER_GAME_MODE_REJECTED("player_game_mode_rejected"),
    @SerializedName("entity_action_execution_failed")
    ENTITY_ACTION_EXECUTION_FAILED("entity_action_execution_failed");

    private final String id;

    EntityActionErrorCode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
