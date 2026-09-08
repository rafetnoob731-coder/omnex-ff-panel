#include <jni.h>
#include <string>
#include <thread>
#include <atomic>
#include <map>
#include <mutex>
#include <vector>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <unistd.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <dirent.h>
#include <android/log.h>

#define LOG_TAG "OMNEX_CORE"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace Offsets {
    const uint64_t PLAYER_CONTROLLER = 0x01A2B3C0;
    const uint64_t PLAYER_HEALTH = 0x1A4;
    const uint64_t PLAYER_ARMOR = 0x1A8;
    const uint64_t PLAYER_POSITION = 0x1B0;
    const uint64_t PLAYER_VELOCITY = 0x1C0;
    const uint64_t PLAYER_SPEED = 0x1C4;
    const uint64_t PLAYER_JUMP_FORCE = 0x1C8;
    const uint64_t PLAYER_COLLISION = 0x1CC;
    const uint64_t PLAYER_IS_ALIVE = 0x1D0;
    const uint64_t PLAYER_VISIBILITY = 0x1D8;
    const uint64_t PLAYER_CLIP = 0x1DC;
    const uint64_t PLAYER_FORWARD = 0x1E0;
    const uint64_t PLAYER_FLYING = 0x1F0;
    const uint64_t PLAYER_GRAVITY = 0x204;
    const uint64_t WEAPON_MANAGER = 0x01B4C5D0;
    const uint64_t CURRENT_WEAPON = 0x210;
    const uint64_t WEAPON_AMMO = 0x214;
    const uint64_t WEAPON_DAMAGE = 0x220;
    const uint64_t WEAPON_RECOIL = 0x230;
    const uint64_t WEAPON_SPREAD = 0x234;
    const uint64_t WEAPON_TRIGGER = 0x238;
    const uint64_t WEAPON_FIRE_RATE = 0x23C;
    const uint64_t WEAPON_RELOAD = 0x240;
    const uint64_t CAMERA_MANAGER = 0x01C6D7E0;
    const uint64_t CAMERA_POSITION = 0x240;
    const uint64_t CAMERA_ROTATION = 0x250;
    const uint64_t CAMERA_FOV = 0x260;
    const uint64_t ENTITY_LIST_BASE = 0x01D8E9F0;
    const uint64_t ENTITY_LIST_SIZE = 0x300;
    const uint64_t ENTITY_STRIDE = 0x8;
    const uint64_t ENTITY_NAME = 0x10;
    const uint64_t ENTITY_HEALTH = 0x1A4;
    const uint64_t ENTITY_POSITION = 0x1B0;
    const uint64_t ENTITY_TEAM = 0x310;
    const uint64_t ENTITY_RANK = 0x320;
    const uint64_t BULLET_TARGET = 0x2A0;
    const uint64_t BULLET_COLLISION = 0x2B0;
    const uint64_t BULLET_PENETRATION = 0x2C0;
    const uint64_t BULLET_RANGE = 0x2D0;
    const uint64_t BULLET_SPEED = 0x2E0;
    const uint64_t BULLET_SPLIT = 0x2F0;
    const uint64_t GLOO_VISIBILITY = 0x504;
    const uint64_t GLOO_DURATION = 0x508;
    const uint64_t GLOO_HP = 0x50C;
    const uint64_t GLOO_THROW = 0x510;
    const uint64_t GLOO_SIZE = 0x514;
    const uint64_t GLOO_AMMO = 0x518;
    const uint64_t VEHICLE_SPEED = 0x400;
    const uint64_t VEHICLE_FLY = 0x404;
    const uint64_t VEHICLE_HEALTH = 0x408;
    const uint64_t VEHICLE_FUEL = 0x40C;
    const uint64_t VEHICLE_BOOST = 0x410;
    const uint64_t VEHICLE_COLLISION = 0x418;
    const uint64_t AURA_RADIUS = 0x700;
    const uint64_t AURA_KILL = 0x704;
    const uint64_t AURA_DAMAGE = 0x708;
    const uint64_t AURA_HEAL = 0x70C;
    const uint64_t AURA_SHIELD = 0x710;
    const uint64_t AURA_VISION = 0x714;
    const uint64_t AURA_FEAR = 0x718;
    const uint64_t AC_BYPASS = 0x900;
    const uint64_t AC_DETECT = 0x904;
    const uint64_t AC_INTEGRITY = 0x908;
    const uint64_t AC_PTRACE_CHECK = 0x90C;
    const uint64_t AC_MEMORY_SCAN = 0x910;
    const uint64_t AC_ROOT_CHECK = 0x914;
    const uint64_t AC_EMULATOR_CHECK = 0x918;
}

static std::atomic<bool> isRunning{true};
static std::atomic<bool> isInjected{false};
static std::atomic<bool> acBypassed{false};
static std::map<std::string, bool> featureStates;
static std::map<std::string, float> featureValues;
static std::mutex featureMutex;

static int memfd = -1;
static pid_t gamePid = -1;
static uint64_t il2cppBase = 0;
static uint64_t gameAssemblyBase = 0;

static std::thread aimbotThread;
static std::thread espThread;
static std::thread auraThread;
static std::thread magicBulletThread;
static std::thread playerModThread;
static std::thread flyThread;
static std::thread vehicleThread;
static std::thread glooThread;
static std::thread bypassThread;

class Memory {
public:
    static bool attach(pid_t pid) {
        if (ptrace(PTRACE_ATTACH, pid, nullptr, nullptr) == -1) {
            LOGE("Failed to attach ptrace");
            return false;
        }
        waitpid(pid, nullptr, 0);
        char path[64];
        snprintf(path, sizeof(path), "/proc/%d/mem", pid);
        memfd = open(path, O_RDWR);
        if (memfd < 0) {
            LOGE("Failed to open mem");
            ptrace(PTRACE_DETACH, pid, nullptr, nullptr);
            return false;
        }
        gamePid = pid;
        return true;
    }

    static void detach() {
        if (memfd > 0) { close(memfd); memfd = -1; }
        if (gamePid > 0) {
            ptrace(PTRACE_DETACH, gamePid, nullptr, nullptr);
            gamePid = -1;
        }
    }

    static uint64_t getModuleBase(const char* moduleName) {
        char maps[16384];
        char path[64];
        snprintf(path, sizeof(path), "/proc/%d/maps", gamePid);
        int fd = open(path, O_RDONLY);
        if (fd < 0) return 0;
        ssize_t bytesRead = read(fd, maps, sizeof(maps) - 1);
        close(fd);
        if (bytesRead <= 0) return 0;
        maps[bytesRead] = '\0';
        char* line = strtok(maps, "\n");
        while (line != nullptr) {
            if (strstr(line, moduleName) != nullptr && strstr(line, "r-xp") != nullptr) {
                char* end = strchr(line, '-');
                if (end != nullptr) {
                    *end = '\0';
                    return strtoull(line, nullptr, 16);
                }
            }
            line = strtok(nullptr, "\n");
        }
        return 0;
    }

    template<typename T>
    static T read(uint64_t address) {
        if (memfd < 0) return T{};
        T value;
        if (pread(memfd, &value, sizeof(T), (off_t)address) != sizeof(T)) return T{};
        return value;
    }

    template<typename T>
    static void write(uint64_t address, T value) {
        if (memfd < 0) return;
        pwrite(memfd, &value, sizeof(T), (off_t)address);
    }

    static void readBytes(uint64_t address, void* buffer, size_t size) {
        if (memfd < 0) return;
        pread(memfd, buffer, size, (off_t)address);
    }
    static void writeBytes(uint64_t address, const void* buffer, size_t size) {
        if (memfd < 0) return;
        pwrite(memfd, buffer, size, (off_t)address);
    }
    static float readFloat(uint64_t address) { return read<float>(address); }
    static int readInt(uint64_t address) { return read<int>(address); }
    static uint64_t read64(uint64_t address) { return read<uint64_t>(address); }
    static bool readBool(uint64_t address) { return read<uint8_t>(address) != 0; }
    static void writeFloat(uint64_t address, float value) { write<float>(address, value); }
    static void writeInt(uint64_t address, int value) { write<int>(address, value); }
    static void writeBool(uint64_t address, bool value) { write<uint8_t>(address, value ? 1 : 0); }
};

void bypassAntiCheat() {
    if (!acBypassed) {
        uint32_t retZero = 0xD2800000;
        uint32_t nop = 0xD503201F;
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_DETECT, retZero);
        uint64_t acIntegrity = il2cppBase + Offsets::AC_INTEGRITY;
        for (int i = 0; i < 30; i++) Memory::write<uint32_t>(acIntegrity + (i * 4), nop);
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_PTRACE_CHECK, retZero);
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_MEMORY_SCAN, retZero);
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_ROOT_CHECK, retZero);
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_EMULATOR_CHECK, retZero);
        Memory::write<uint32_t>(il2cppBase + Offsets::AC_BYPASS, 0xFFFFFFFF);
        acBypassed = true;
        LOGI("Anti-cheat bypassed successfully");
    }
}

struct Entity {
    uint64_t address;
    float posX, posY, posZ;
    float health;
    int team;
    int rank;
    char name[64];
    bool isAlive;
};

std::vector<Entity> getEntityList() {
    std::vector<Entity> entities;
    if (il2cppBase == 0) return entities;
    uint64_t entityList = Memory::read64(il2cppBase + Offsets::ENTITY_LIST_BASE);
    if (entityList == 0) return entities;
    int entityCount = Memory::readInt(entityList + Offsets::ENTITY_LIST_SIZE);
    if (entityCount <= 0 || entityCount > 200) entityCount = 100;
    for (int i = 0; i < entityCount; i++) {
        uint64_t entityAddr = Memory::read64(entityList + (i * Offsets::ENTITY_STRIDE));
        if (entityAddr == 0 || entityAddr < 0x1000000) continue;
        Entity entity;
        entity.address = entityAddr;
        entity.posX = Memory::readFloat(entityAddr + Offsets::ENTITY_POSITION);
        entity.posY = Memory::readFloat(entityAddr + Offsets::ENTITY_POSITION + 4);
        entity.posZ = Memory::readFloat(entityAddr + Offsets::ENTITY_POSITION + 8);
        entity.health = Memory::readFloat(entityAddr + Offsets::ENTITY_HEALTH);
        entity.team = Memory::readInt(entityAddr + Offsets::ENTITY_TEAM);
        entity.rank = Memory::readInt(entityAddr + Offsets::ENTITY_RANK);
        entity.isAlive = entity.health > 0.0f && entity.health < 10000.0f;
        uint64_t namePtr = Memory::read64(entityAddr + Offsets::ENTITY_NAME);
        if (namePtr != 0 && namePtr < 0x7FFFFFFFFFFF) {
            Memory::readBytes(namePtr, entity.name, 63);
            entity.name[63] = '\0';
        } else {
            strcpy(entity.name, "Player");
        }
        if (entity.isAlive) entities.push_back(entity);
    }
    return entities;
}

uint64_t getLocalPlayer() {
    if (il2cppBase == 0) return 0;
    return Memory::read64(il2cppBase + Offsets::PLAYER_CONTROLLER);
}

float getDistance(float x1, float y1, float z1, float x2, float y2, float z2) {
    return sqrtf(powf(x2 - x1, 2) + powf(y2 - y1, 2) + powf(z2 - z1, 2));
}

void aimbotLoop() {
    while (isRunning) {
        bool anyAim = featureStates["aimbot"] || featureStates["aim_silent"] ||
                       featureStates["aim_logic"] || featureStates["triggerbot"] ||
                       featureStates["ai_aim"];
        if (!anyAim) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        uint64_t localPlayer = getLocalPlayer();
        if (localPlayer == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        float localX = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION);
        float localY = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 4);
        float localZ = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 8);
        int localTeam = Memory::readInt(localPlayer + Offsets::ENTITY_TEAM);
        auto entities = getEntityList();
        Entity* nearestEnemy = nullptr;
        Entity* lowestHealth = nullptr;
        float nearestDist = 999999.0f;
        float lowestHp = 999999.0f;
        for (auto& entity : entities) {
            if (entity.team == localTeam || entity.address == localPlayer) continue;
            float dist = getDistance(localX, localY, localZ, entity.posX, entity.posY, entity.posZ);
            if (dist < nearestDist) { nearestDist = dist; nearestEnemy = &entity; }
            if (entity.health < lowestHp) { lowestHp = entity.health; lowestHealth = &entity; }
        }
        Entity* target = featureStates["aim_logic"] ? lowestHealth : nearestEnemy;
        if (target != nullptr) {
            float yaw = atan2f(target->posZ - localZ, target->posX - localX) * (180.0f / M_PI);
            float pitch = atan2f(target->posY - localY, sqrtf(powf(target->posX - localX, 2) + powf(target->posZ - localZ, 2))) * (180.0f / M_PI);
            uint64_t camera = Memory::read64(il2cppBase + Offsets::CAMERA_MANAGER);
            if (camera != 0) {
                if (featureStates["aimbot"] || featureStates["ai_aim"]) {
                    float smooth = featureValues["aim_smoothness"];
                    if (smooth <= 0) smooth = 5.0f;
                    float currentYaw = Memory::readFloat(camera + Offsets::CAMERA_ROTATION);
                    float currentPitch = Memory::readFloat(camera + Offsets::CAMERA_ROTATION + 4);
                    Memory::writeFloat(camera + Offsets::CAMERA_ROTATION, currentYaw + (yaw - currentYaw) / smooth);
                    Memory::writeFloat(camera + Offsets::CAMERA_ROTATION + 4, currentPitch + (pitch - currentPitch) / smooth);
                }
                if (featureStates["aim_silent"]) {
                    uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
                    if (weapon != 0) {
                        Memory::writeFloat(weapon + Offsets::BULLET_TARGET, target->posX);
                        Memory::writeFloat(weapon + Offsets::BULLET_TARGET + 4, target->posY);
                        Memory::writeFloat(weapon + Offsets::BULLET_TARGET + 8, target->posZ);
                    }
                }
                if (featureStates["triggerbot"]) {
                    Memory::writeBool(localPlayer + Offsets::WEAPON_TRIGGER, true);
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
}

void espLoop() {
    while (isRunning) {
        if (!featureStates["esp_master"]) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        std::this_thread::sleep_for(std::chrono::milliseconds(16));
    }
}

void playerModLoop() {
    while (isRunning) {
        bool anyMod = featureStates["speed_hack"] || featureStates["jump_hack"] || featureStates["god_mode"] ||
                       featureStates["unlimited_ammo"] || featureStates["rapid_fire"] ||
                       featureStates["instant_reload"] || featureStates["damage_multiplier"] ||
                       featureStates["invisibility"] || featureStates["no_clip"] ||
                       featureStates["no_recoil"] || featureStates["no_spread"] || featureStates["wall_hack"];
        if (!anyMod) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        uint64_t localPlayer = getLocalPlayer();
        if (localPlayer == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (featureStates["speed_hack"]) {
            float speedMult = featureValues["speed_value"]; if (speedMult <= 0) speedMult = 2.0f;
            Memory::writeFloat(localPlayer + Offsets::PLAYER_SPEED, 5.0f * speedMult);
        }
        if (featureStates["jump_hack"]) {
            float jumpMult = featureValues["jump_value"]; if (jumpMult <= 0) jumpMult = 3.0f;
            Memory::writeFloat(localPlayer + Offsets::PLAYER_JUMP_FORCE, 10.0f * jumpMult);
        }
        if (featureStates["god_mode"]) {
            Memory::writeFloat(localPlayer + Offsets::PLAYER_HEALTH, 9999.0f);
            Memory::writeFloat(localPlayer + Offsets::PLAYER_ARMOR, 9999.0f);
        }
        if (featureStates["unlimited_ammo"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) Memory::writeInt(weapon + Offsets::WEAPON_AMMO, 999);
        }
        if (featureStates["rapid_fire"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) Memory::writeFloat(weapon + Offsets::WEAPON_FIRE_RATE, 0.01f);
        }
        if (featureStates["instant_reload"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) Memory::writeFloat(weapon + Offsets::WEAPON_RELOAD, 0.0f);
        }
        if (featureStates["damage_multiplier"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) {
                float dmgMult = featureValues["damage_value"]; if (dmgMult <= 0) dmgMult = 10.0f;
                Memory::writeFloat(weapon + Offsets::WEAPON_DAMAGE, 25.0f * dmgMult);
            }
        }
        if (featureStates["invisibility"]) Memory::writeBool(localPlayer + Offsets::PLAYER_VISIBILITY, false);
        if (featureStates["no_clip"]) Memory::writeBool(localPlayer + Offsets::PLAYER_CLIP, true);
        if (featureStates["no_recoil"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) Memory::writeFloat(weapon + Offsets::WEAPON_RECOIL, 0.0f);
        }
        if (featureStates["no_spread"]) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) Memory::writeFloat(weapon + Offsets::WEAPON_SPREAD, 0.0f);
        }
        if (featureStates["wall_hack"]) Memory::writeBool(localPlayer + Offsets::PLAYER_COLLISION, false);
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

void flyLoop() {
    while (isRunning) {
        bool anyFly = featureStates["fly_master"] || featureStates["fly_up"] || featureStates["fly_down"] ||
                       featureStates["fly_forward"] || featureStates["fly_hover"] ||
                       featureStates["fly_gravity"] || featureStates["fly_boost"];
        if (!anyFly) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        uint64_t localPlayer = getLocalPlayer();
        if (localPlayer == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (featureStates["fly_master"]) {
            Memory::writeBool(localPlayer + Offsets::PLAYER_FLYING, true);
            Memory::writeFloat(localPlayer + Offsets::PLAYER_GRAVITY, 0.0f);
        }
        if (featureStates["fly_up"]) {
            float flySpeed = featureValues["fly_speed"]; if (flySpeed <= 0) flySpeed = 10.0f;
            Memory::writeFloat(localPlayer + Offsets::PLAYER_VELOCITY + 4, flySpeed);
        }
        if (featureStates["fly_down"]) {
            float flySpeed = featureValues["fly_speed"]; if (flySpeed <= 0) flySpeed = 10.0f;
            Memory::writeFloat(localPlayer + Offsets::PLAYER_VELOCITY + 4, -flySpeed);
        }
        if (featureStates["fly_hover"]) {
            float hoverHeight = featureValues["hover_height"]; if (hoverHeight <= 0) hoverHeight = 20.0f;
            float currentY = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 4);
            float vel = (currentY < hoverHeight) ? 5.0f : ((currentY > hoverHeight) ? -5.0f : 0.0f);
            Memory::writeFloat(localPlayer + Offsets::PLAYER_VELOCITY + 4, vel);
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

void magicBulletLoop() {
    while (isRunning) {
        bool anyMB = featureStates["magic_bullet"] || featureStates["bullet_penetration"] ||
                      featureStates["bullet_damage"] || featureStates["bullet_range"] ||
                      featureStates["bullet_speed"] || featureStates["bullet_split"];
        if (!anyMB) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        uint64_t localPlayer = getLocalPlayer();
        if (localPlayer == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(10)); continue; }
        float localX = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION);
        float localY = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 4);
        float localZ = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 8);
        int localTeam = Memory::readInt(localPlayer + Offsets::ENTITY_TEAM);
        auto entities = getEntityList();
        Entity* nearestEnemy = nullptr;
        float nearestDist = 999999.0f;
        for (auto& entity : entities) {
            if (entity.team == localTeam) continue;
            float dist = getDistance(localX, localY, localZ, entity.posX, entity.posY, entity.posZ);
            if (dist < nearestDist) { nearestDist = dist; nearestEnemy = &entity; }
        }
        if (nearestEnemy != nullptr) {
            uint64_t weapon = Memory::read64(localPlayer + Offsets::CURRENT_WEAPON);
            if (weapon != 0) {
                if (featureStates["magic_bullet"]) {
                    Memory::writeFloat(weapon + Offsets::BULLET_TARGET, nearestEnemy->posX);
                    Memory::writeFloat(weapon + Offsets::BULLET_TARGET + 4, nearestEnemy->posY);
                    Memory::writeFloat(weapon + Offsets::BULLET_TARGET + 8, nearestEnemy->posZ);
                    Memory::writeBool(weapon + Offsets::BULLET_COLLISION, false);
                }
                if (featureStates["bullet_penetration"]) Memory::writeBool(weapon + Offsets::BULLET_PENETRATION, true);
                if (featureStates["bullet_damage"]) {
                    float dmg = featureValues["bullet_damage_value"]; if (dmg <= 0) dmg = 100.0f;
                    Memory::writeFloat(weapon + Offsets::WEAPON_DAMAGE, dmg);
                }
                if (featureStates["bullet_range"]) Memory::writeFloat(weapon + Offsets::BULLET_RANGE, 9999.0f);
                if (featureStates["bullet_speed"]) Memory::writeFloat(weapon + Offsets::BULLET_SPEED, 9999.0f);
                if (featureStates["bullet_split"]) {
                    int splitCount = (int)featureValues["bullet_split_count"]; if (splitCount <= 0) splitCount = 5;
                    Memory::writeInt(weapon + Offsets::BULLET_SPLIT, splitCount);
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
}

void auraLoop() {
    while (isRunning) {
        bool anyAura = featureStates["aura_master"] || featureStates["aura_kill"] || featureStates["aura_damage"] ||
                        featureStates["aura_heal"] || featureStates["aura_shield"] ||
                        featureStates["aura_vision"] || featureStates["aura_fear"];
        if (!anyAura) { std::this_thread::sleep_for(std::chrono::milliseconds(100)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(100)); continue; }
        uint64_t localPlayer = getLocalPlayer();
        if (localPlayer == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(100)); continue; }
        float localX = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION);
        float localY = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 4);
        float localZ = Memory::readFloat(localPlayer + Offsets::PLAYER_POSITION + 8);
        int localTeam = Memory::readInt(localPlayer + Offsets::ENTITY_TEAM);
        auto entities = getEntityList();
        float auraRadius = featureValues["aura_radius"]; if (auraRadius <= 0) auraRadius = 50.0f;
        for (auto& entity : entities) {
            float dist = getDistance(localX, localY, localZ, entity.posX, entity.posY, entity.posZ);
            if (dist <= auraRadius) {
                if (entity.team != localTeam) {
                    if (featureStates["aura_kill"]) Memory::writeFloat(entity.address + Offsets::ENTITY_HEALTH, 0.0f);
                    else if (featureStates["aura_damage"]) {
                        float currentHealth = Memory::readFloat(entity.address + Offsets::ENTITY_HEALTH);
                        float dmg = featureValues["aura_damage_value"]; if (dmg <= 0) dmg = 25.0f;
                        Memory::writeFloat(entity.address + Offsets::ENTITY_HEALTH, currentHealth - dmg);
                    }
                } else {
                    if (featureStates["aura_heal"]) {
                        float currentHealth = Memory::readFloat(entity.address + Offsets::ENTITY_HEALTH);
                        if (currentHealth < 100.0f) Memory::writeFloat(entity.address + Offsets::ENTITY_HEALTH, currentHealth + 5.0f);
                    }
                    if (featureStates["aura_shield"]) Memory::writeFloat(entity.address + Offsets::PLAYER_ARMOR, 200.0f);
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
    }
}

void vehicleLoop() {
    while (isRunning) {
        bool anyVeh = featureStates["vehicle_speed"] || featureStates["vehicle_fly"] ||
                        featureStates["vehicle_god"] || featureStates["no_fuel"] ||
                        featureStates["vehicle_boost"] || featureStates["vehicle_spawn"];
        if (!anyVeh) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
}

void glooLoop() {
    while (isRunning) {
        if (!featureStates["gloo_master"]) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        if (!isInjected || il2cppBase == 0) { std::this_thread::sleep_for(std::chrono::milliseconds(50)); continue; }
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
    }
}

void bypassLoop() {
    while (isRunning) {
        if (!acBypassed) bypassAntiCheat();
        std::this_thread::sleep_for(std::chrono::milliseconds(5000));
    }
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_omnex_ffpanel_OmnexApp_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Native library initialized");
}

JNIEXPORT void JNICALL
Java_com_omnex_ffpanel_MainActivity_initNative(JNIEnv* env, jobject thiz) {
    LOGI("initNative called");
}

JNIEXPORT jlong JNICALL
Java_com_omnex_ffpanel_MainActivity_getGamePid(JNIEnv* env, jobject thiz) {
    DIR* dir = opendir("/proc");
    if (dir == nullptr) return -1;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_type != DT_DIR) continue;
        pid_t pid = atoi(entry->d_name);
        if (pid <= 0) continue;
        char cmdlinePath[64];
        snprintf(cmdlinePath, sizeof(cmdlinePath), "/proc/%d/cmdline", pid);
        int fd = open(cmdlinePath, O_RDONLY);
        if (fd < 0) continue;
        char cmdline[256] = {0};
        read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        if (strstr(cmdline, "freefire") != nullptr || strstr(cmdline, "dts.freefire") != nullptr ||
            strstr(cmdline, "com.dts.freefireth") != nullptr) {
            closedir(dir);
            return (jlong)pid;
        }
    }
    closedir(dir);
    return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_omnex_ffpanel_MainActivity_injectGame(JNIEnv* env, jobject thiz, jint pid) {
    gamePid = pid;
    if (!Memory::attach(pid)) { LOGE("Failed to attach to game"); return JNI_FALSE; }
    il2cppBase = Memory::getModuleBase("libil2cpp.so");
    if (il2cppBase == 0) il2cppBase = Memory::getModuleBase("libunity.so");
    if (il2cppBase == 0) { LOGE("Failed to find il2cpp base"); Memory::detach(); return JNI_FALSE; }
    LOGI("il2cpp base: 0x%llx", (unsigned long long)il2cppBase);
    bypassAntiCheat();
    isInjected = true;
    aimbotThread = std::thread(aimbotLoop);
    espThread = std::thread(espLoop);
    auraThread = std::thread(auraLoop);
    magicBulletThread = std::thread(magicBulletLoop);
    playerModThread = std::thread(playerModLoop);
    flyThread = std::thread(flyLoop);
    vehicleThread = std::thread(vehicleLoop);
    glooThread = std::thread(glooLoop);
    bypassThread = std::thread(bypassLoop);
    LOGI("Game injected successfully - All threads started");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_omnex_ffpanel_MainActivity_isInjected(JNIEnv* env, jobject thiz) {
    return isInjected ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_omnex_ffpanel_MainActivity_bypassAntiCheat(JNIEnv* env, jobject thiz) {
    bypassAntiCheat();
}

JNIEXPORT void JNICALL
Java_com_omnex_ffpanel_FloatingService_setFeature(JNIEnv* env, jobject thiz, jstring feature, jboolean enabled) {
    const char* featureStr = env->GetStringUTFChars(feature, nullptr);
    std::lock_guard<std::mutex> lock(featureMutex);
    featureStates[featureStr] = enabled;
    env->ReleaseStringUTFChars(feature, featureStr);
    LOGI("Feature %s set to %s", featureStr, enabled ? "ON" : "OFF");
}

JNIEXPORT void JNICALL
Java_com_omnex_ffpanel_FloatingService_setValue(JNIEnv* env, jobject thiz, jstring key, jfloat value) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    std::lock_guard<std::mutex> lock(featureMutex);
    featureValues[keyStr] = value;
    env->ReleaseStringUTFChars(key, keyStr);
    LOGI("Value %s set to %f", keyStr, value);
}

JNIEXPORT jboolean JNICALL
Java_com_omnex_ffpanel_FloatingService_isInjected(JNIEnv* env, jobject thiz) {
    return isInjected ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_omnex_ffpanel_FloatingService_getGamePid(JNIEnv* env, jobject thiz) {
    return (jlong)gamePid;
}

} // extern "C"