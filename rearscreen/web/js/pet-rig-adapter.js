(function (root) {
  "use strict";

  var DEFAULT_NAMES = {
    head: ["Head", "head", "J_Bip_C_Head"],
    neck: ["Neck", "neck", "J_Bip_C_Neck"],
    spine: ["spine2", "spine1", "Spine", "Spine1", "Spine2", "J_Bip_C_Spine"],
    arms: ["Upperarm L", "Upperarm R", "LeftArm", "RightArm", "J_Bip_L_UpperArm", "J_Bip_R_UpperArm"],
    ears: ["Ear_01_L", "Ear_01_R", "Ear_L", "Ear_R"],
    tail: ["Tail", "しっぽ"],
    hair: ["Hair"],
    cloth: ["Mantle", "Skirt", "Cloth"]
  };

  var PATTERNS = {
    head: [/^head$/i, /head.*bone/i, /j_bip_c_head/i],
    neck: [/^neck$/i, /j_bip_c_neck/i],
    spine: [/^spine[0-9]*$/i, /^spine[0-9]+$/i, /j_bip_c_spine/i],
    arms: [/upperarm/i, /upper_arm/i, /arm.*upper/i, /j_bip_[lr]_upperarm/i],
    ears: [/^ear([_0-9]|$)/i, /ear.*(l|r|left|right)/i],
    tail: [/tail/i, /しっぽ/],
    hair: [/hair/i],
    cloth: [/skirt/i, /mantle/i, /cloth/i, /sleeve/i, /ribbon/i]
  };

  function normalize(value) {
    return String(value || "").toLowerCase().replace(/[\s_\-.:：]/g, "");
  }

  function asArray(value) {
    if (!value) return [];
    return Object.prototype.toString.call(value) === "[object Array]" ? value : [value];
  }

  function collect(model) {
    var bones = [];
    var meshes = [];
    if (!model || typeof model.traverse !== "function") {
      throw new Error("HyperCatRig: expected a THREE.Object3D/GLTF scene");
    }
    model.traverse(function (node) {
      if (node && (node.isBone || node.type === "Bone")) bones.push(node);
      if (node && (node.isMesh || node.isSkinnedMesh)) meshes.push(node);
    });
    return { bones: bones, meshes: meshes };
  }

  function findByAliases(bones, aliases) {
    var i, j, target;
    for (i = 0; i < aliases.length; i++) {
      target = normalize(aliases[i]);
      for (j = 0; j < bones.length; j++) {
        if (normalize(bones[j].name) === target) return bones[j];
      }
    }
    return null;
  }

  function findByPatterns(bones, patterns, used) {
    var i, j, name;
    for (i = 0; i < patterns.length; i++) {
      for (j = 0; j < bones.length; j++) {
        name = String(bones[j].name || "");
        if (!used[bones[j].uuid] && patterns[i].test(name)) return bones[j];
      }
    }
    return null;
  }

  function discover(model, options) {
    options = options || {};
    var found = collect(model);
    var overrides = options.bones || {};
    var aliases = options.aliases || {};
    var rig = {};
    var used = {};
    var groups = ["head", "neck", "spine", "arms", "ears", "tail", "hair", "cloth"];
    var i, key, list, explicit, bone, j;

    for (i = 0; i < groups.length; i++) {
      key = groups[i];
      rig[key] = [];
      explicit = asArray(overrides[key]);
      if (!explicit.length) explicit = asArray(aliases[key]);
      if (!explicit.length) explicit = DEFAULT_NAMES[key];

      for (j = 0; j < explicit.length; j++) {
        bone = findByAliases(found.bones, [explicit[j]]);
        if (bone && !used[bone.uuid]) {
          rig[key].push(bone);
          used[bone.uuid] = true;
        }
      }

      var hasExplicitMap = Object.prototype.hasOwnProperty.call(overrides, key) ||
        Object.prototype.hasOwnProperty.call(aliases, key);
      if (!hasExplicitMap) {
        list = rig[key].slice();
        bone = findByPatterns(found.bones, PATTERNS[key], used);
        while (bone) {
          list.push(bone);
          used[bone.uuid] = true;
          bone = findByPatterns(found.bones, PATTERNS[key], used);
        }
        rig[key] = list;
      } else if (!rig[key].length) {
        bone = findByPatterns(found.bones, PATTERNS[key], used);
        while (bone) {
          rig[key].push(bone);
          used[bone.uuid] = true;
          bone = findByPatterns(found.bones, PATTERNS[key], used);
        }
      }
    }

    var clipNames = [];
    asArray(options.animations).forEach(function (clip) {
      if (clip && clip.name) clipNames.push(String(clip.name).toLowerCase());
    });

    var explicitFace = !!(options.expression && options.expression.enabled === true);
    var faceTargets = [];
    found.meshes.forEach(function (mesh) {
      if (/eyes?|mouth|eyebrow|brow|face/i.test(mesh.name || "")) faceTargets.push(mesh);
    });

    var capabilities = {
      headLook: rig.head.length > 0 || rig.neck.length > 0,
      breathing: rig.spine.length > 0,
      armPose: rig.arms.length > 0,
      earPhysics: rig.ears.length > 0,
      tailPhysics: rig.tail.length > 0,
      hairPhysics: rig.hair.length > 0,
      clothPhysics: rig.cloth.length > 0,
      touchInteraction: found.meshes.length > 0,
      expression: explicitFace,
      blink: explicitFace && options.expression.blink === true,
      danceAnimation: clipNames.some(function (n) { return /dance|dancing|music/.test(n); }),
      walkAnimation: clipNames.some(function (n) { return /walk|walking/.test(n); }),
      runAnimation: clipNames.some(function (n) { return /run|running/.test(n); })
    };

    return {
      rig: rig,
      bones: found.bones,
      meshes: found.meshes,
      faceCandidates: faceTargets,
      capabilities: capabilities,
      missing: groups.filter(function (name) { return rig[name].length === 0; })
    };
  }

  function summary(result) {
    var out = {};
    Object.keys(result.capabilities).forEach(function (key) {
      out[key] = !!result.capabilities[key];
    });
    return {
      boneCount: result.bones.length,
      meshCount: result.meshes.length,
      rigCounts: {
        head: result.rig.head.length,
        neck: result.rig.neck.length,
        spine: result.rig.spine.length,
        arms: result.rig.arms.length,
        ears: result.rig.ears.length,
        tail: result.rig.tail.length,
        hair: result.rig.hair.length,
        cloth: result.rig.cloth.length
      },
      capabilities: out,
      missingGroups: result.missing.slice()
    };
  }

  root.HyperCatRig = {
    version: "1.0.0",
    discover: discover,
    summary: summary,
    normalizeName: normalize
  };
}(this));
