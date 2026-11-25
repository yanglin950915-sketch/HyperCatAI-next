(function (root) {
  "use strict";

  var MOODS = {
    idle:     { face: "normal", pose: "idle", locomotion: "idle" },
    happy:    { face: "excited", pose: "happy", locomotion: "idle" },
    sad:      { face: "normal", pose: "sad", locomotion: "idle" },
    angry:    { face: "normal", pose: "angry", locomotion: "idle" },
    sleepy:   { face: "normal", pose: "sleepy", locomotion: "idle" },
    thinking: { face: "normal", pose: "thinking", locomotion: "idle" },
    excited:  { face: "excited", pose: "excited", locomotion: "idle" }
  };

  var ACTIONS = {
    wave: "wave",
    nod: "nod",
    dance: "dance",
    stretch: "stretch",
    stumble: "stumble",
    petReaction: "petReaction"
  };

  function createController(rigResult, dispatch) {
    if (!rigResult || !rigResult.capabilities) {
      throw new Error("HyperCatBehavior: rig discovery result is required");
    }
    if (typeof dispatch !== "function") {
      throw new Error("HyperCatBehavior: dispatch callback is required");
    }

    var caps = rigResult.capabilities;
    var currentMood = "idle";

    function emit(type, payload) {
      dispatch({ type: type, payload: payload || {} });
    }

    function setMood(mood, durationMs) {
      var key = String(mood || "idle");
      var state = MOODS[key] || MOODS.idle;
      currentMood = MOODS[key] ? key : "idle";
      emit("mood", {
        mood: currentMood,
        face: caps.expression ? state.face : null,
        pose: state.pose,
        headLook: caps.headLook && (state.pose === "thinking" || state.pose === "idle"),
        breathing: caps.breathing && state.pose !== "excited",
        durationMs: Math.max(0, Number(durationMs) || 0)
      });
      return currentMood;
    }

    function playAction(action, options) {
      var key = String(action || "");
      if (!ACTIONS[key]) return false;
      options = options || {};

      if (key === "dance" && !caps.danceAnimation) {
        emit("fallback", { action: key, reason: "animation-unavailable", mood: currentMood });
        return false;
      }
      if ((key === "wave" || key === "stretch") && !caps.armPose) {
        emit("fallback", { action: key, reason: "arm-rig-unavailable", mood: currentMood });
        return false;
      }
      if (key === "petReaction" && !caps.touchInteraction) return false;

      emit("action", { action: key, options: options, mood: currentMood });
      return true;
    }

    function setLocomotion(state) {
      var key = String(state || "idle");
      if (key === "walk" && !caps.walkAnimation) key = "idle";
      if (key === "run" && !caps.runAnimation) key = caps.walkAnimation ? "walk" : "idle";
      emit("locomotion", { state: key });
      return key;
    }

    function onTouch(target) {
      emit("touch", { target: target || "body" });
      return playAction("petReaction", { target: target || "body" });
    }

    return {
      setMood: setMood,
      playAction: playAction,
      setLocomotion: setLocomotion,
      onTouch: onTouch,
      getMood: function () { return currentMood; }
    };
  }

  root.HyperCatBehavior = {
    version: "1.0.0",
    createController: createController,
    moods: Object.keys(MOODS),
    actions: Object.keys(ACTIONS)
  };
}(this));
