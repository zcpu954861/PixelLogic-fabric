package com.pixelmc.pixellogic.core.runtime;

import com.google.gson.annotations.SerializedName;

public enum EntityTargetErrorCode {
    @SerializedName("entity_target_invalid_config")
    ENTITY_TARGET_INVALID_CONFIG("entity_target_invalid_config"),
    @SerializedName("entity_target_missing")
    ENTITY_TARGET_MISSING("entity_target_missing"),
    @SerializedName("entity_target_unresolvable")
    ENTITY_TARGET_UNRESOLVABLE("entity_target_unresolvable"),
    @SerializedName("entity_target_offline")
    ENTITY_TARGET_OFFLINE("entity_target_offline"),
    @SerializedName("entity_target_type_mismatch")
    ENTITY_TARGET_TYPE_MISMATCH("entity_target_type_mismatch"),
    @SerializedName("entity_target_not_alive")
    ENTITY_TARGET_NOT_ALIVE("entity_target_not_alive"),
    @SerializedName("entity_target_provider_unavailable")
    ENTITY_TARGET_PROVIDER_UNAVAILABLE("entity_target_provider_unavailable");

    private final String id;

    EntityTargetErrorCode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
