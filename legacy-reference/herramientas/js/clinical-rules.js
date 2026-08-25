(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ClinicalRules = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const n = (value, fallback = 0) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  };
  const clamp = (value, min = 0, max = 100) => Math.max(min, Math.min(max, value));

  function bodySurfaceArea(weightKg, heightCm) {
    const weight = n(weightKg), height = n(heightCm);
    return weight > 0 && height > 0 ? Math.sqrt(weight * height / 3600) : null;
  }

  function bodyMassIndex(weightKg, heightCm) {
    const weight = n(weightKg), heightM = n(heightCm) / 100;
    if (weight <= 0 || heightM <= 0) return null;
    const value = weight / (heightM * heightM);
    const category = value < 18.5 ? "Bajo peso"
      : value < 25 ? "Rango saludable"
        : value < 30 ? "Sobrepeso"
          : value < 35 ? "Obesidad clase I"
            : value < 40 ? "Obesidad clase II"
              : "Obesidad clase III";
    return { value, category };
  }

  function calvertDose(auc, gfr) {
    const target = n(auc), filtration = n(gfr, -1);
    return target > 0 && filtration > 0 ? target * (filtration + 25) : null;
  }

  function charlson(input = {}) {
    const items = [];
    const add = (key, label, weight) => { if (input[key]) items.push({ key, label, weight }); };
    add("mi", "Infarto de miocardio", 1);
    add("chf", "Insuficiencia cardiaca", 1);
    add("pvd", "Enfermedad vascular periferica", 1);
    add("cva", "Enfermedad cerebrovascular", 1);
    add("dementia", "Demencia", 1);
    add("copd", "Enfermedad pulmonar cronica", 1);
    add("connective", "Enfermedad del tejido conectivo", 1);
    add("ulcer", "Enfermedad ulcerosa", 1);
    if (input.liverSevere) items.push({ key: "liverSevere", label: "Hepatopatia moderada/severa", weight: 3 });
    else add("liverMild", "Hepatopatia leve", 1);
    if (input.diabetesComplicated) items.push({ key: "diabetesComplicated", label: "Diabetes con daño de organo", weight: 2 });
    else add("diabetes", "Diabetes sin daño de organo", 1);
    add("hemiplegia", "Hemiplejia", 2);
    add("renal", "Enfermedad renal moderada/severa", 2);
    if (input.metastaticTumor) items.push({ key: "metastaticTumor", label: "Tumor solido metastasico", weight: 6 });
    else add("solidTumor", "Tumor solido", 2);
    add("leukemia", "Leucemia", 2);
    add("lymphoma", "Linfoma", 2);
    add("aids", "SIDA", 6);
    const age = n(input.age);
    const agePoints = age >= 80 ? 4 : age >= 70 ? 3 : age >= 60 ? 2 : age >= 50 ? 1 : 0;
    const comorbidityPoints = items.reduce((sum, item) => sum + item.weight, 0);
    return { total: comorbidityPoints + agePoints, comorbidityPoints, agePoints, items };
  }

  function g8(input = {}) {
    const keys = ["food", "weight", "mobility", "neuro", "bmi", "meds", "health", "age"];
    const total = keys.reduce((sum, key) => sum + n(input[key]), 0);
    return { total, altered: total <= 14 };
  }

  function carg(input = {}) {
    const weighted = [
      ["age72", 2], ["gigu", 2], ["standard", 2], ["poly", 2], ["hb", 3], ["crcl", 3],
      ["hearing", 2], ["falls", 3], ["medsHelp", 1], ["walk", 2], ["social", 1],
    ];
    const total = weighted.reduce((sum, [key, weight]) => sum + (input[key] ? weight : 0), 0);
    const category = total <= 5 ? "bajo" : total <= 9 ? "intermedio" : "alto";
    const toxicity = category === "bajo" ? 30 : category === "intermedio" ? 52 : 83;
    return { total, category, toxicity };
  }

  function ipss(values = []) {
    const total = values.reduce((sum, value) => sum + n(value), 0);
    return { total, category: total === 0 ? "asintomático" : total <= 7 ? "leve" : total <= 19 ? "moderado" : "severo" };
  }

  function shim(values = []) {
    const total = values.reduce((sum, value) => sum + n(value), 0);
    const category = total >= 22 ? "sin disfuncion erectil significativa" : total >= 17 ? "disfuncion leve" : total >= 12 ? "disfuncion leve-moderada" : total >= 8 ? "disfuncion moderada" : "disfuncion severa";
    return { total, category };
  }

  function eauProstateRisk(input = {}) {
    const psa = n(input.psa), gg = n(input.gg), stage = String(input.ct || "").toLowerCase(), nodes = String(input.n || "nx").toLowerCase(), metastasis = String(input.m || "mx").toLowerCase();
    if (metastasis === "m1") return { key: "metastatic", label: "Fuera de alcance: enfermedad M1" };
    if (metastasis !== "m0") return { key: "unclassified", label: "No clasificable: falta confirmar M0" };
    const nodePositive = nodes === "n1";
    const locallyAdvanced = nodePositive || stage.startsWith("t3") || stage.startsWith("t4");
    if (locallyAdvanced) return { key: "locally_advanced", label: "Localmente avanzado" };
    if (nodes !== "n0") return { key: "unclassified", label: "No clasificable: falta confirmar cN0" };
    if (gg >= 4 || psa > 20) return { key: "high", label: "Alto riesgo localizado" };
    if ((gg === 2 && psa >= 10 && psa <= 20) || gg === 3) return { key: "unfavorable_intermediate", label: "Intermedio desfavorable" };
    if ((gg === 2 && psa < 10) || (gg === 1 && psa >= 10 && psa <= 20)) return { key: "favorable_intermediate", label: "Intermedio favorable" };
    if (gg === 1 && psa < 10) return { key: "low", label: "Bajo riesgo" };
    return { key: "unclassified", label: "No clasificable con estos datos" };
  }

  function capra(input = {}) {
    const age = n(input.age), psa = n(input.psa), primary = n(input.primary), secondary = n(input.secondary), stage = String(input.ct || "").toLowerCase(), positiveCores = n(input.positiveCores), totalCores = n(input.totalCores);
    if (!["t1", "t1c", "t2a", "t2b", "t2c", "t3a"].includes(stage)) return { valid: false, reason: "CAPRA original no incluye cT3b ni cT4" };
    if (totalCores <= 0 || positiveCores < 0 || positiveCores > totalCores) return { valid: false, reason: "Revisar la cantidad de cilindros positivos y totales" };
    let total = age >= 50 ? 1 : 0;
    total += psa > 30 ? 4 : psa > 20 ? 3 : psa > 10 ? 2 : psa > 6 ? 1 : 0;
    total += primary >= 4 ? 3 : secondary >= 4 ? 1 : 0;
    total += stage.startsWith("t3a") ? 1 : 0;
    const corePercent = totalCores > 0 ? positiveCores / totalCores * 100 : 0;
    total += corePercent >= 34 ? 1 : 0;
    total = clamp(total, 0, 10);
    return { valid: true, total, corePercent, category: total <= 2 ? "bajo" : total <= 5 ? "intermedio" : "alto" };
  }

  function capraS(input = {}) {
    const psa = n(input.psa), primary = n(input.primary), secondary = n(input.secondary);
    let total = psa > 20 ? 3 : psa > 10 ? 2 : psa > 6 ? 1 : 0;
    const gleasonSum=primary+secondary;
    total += gleasonSum >= 8 ? 3 : primary === 4 && secondary === 3 ? 2 : primary === 3 && secondary === 4 ? 1 : (primary >= 5 || secondary >= 5 ? 3 : 0);
    total += input.margin ? 2 : 0;
    total += input.ece ? 1 : 0;
    total += input.svi ? 2 : 0;
    total += input.lni ? 1 : 0;
    return { total, category: total <= 2 ? "bajo" : total <= 5 ? "intermedio" : "alto" };
  }

  function psaDensity(psa, volume) {
    const denominator = n(volume);
    return denominator > 0 ? n(psa) / denominator : null;
  }

  function pbcg(input = {}) {
    const psa = n(input.psa), age = n(input.age);
    if (psa < 2 || psa > 50 || age < 40 || age > 90) return { valid: false, reason: "PBCG fue validado para edad 40-90 y PSA 2-50 ng/ml" };
    const x = [1, Math.log2(psa), age, input.african ? 1 : 0, input.priorNegative ? 1 : 0, input.dre ? 1 : 0, input.family ? 1 : 0];
    const lowCoefficients = [-2.44052108,0.13617244,0.01780617,0.78721039,-0.83613721,0.04612721,0.33233636];
    const highCoefficients = [-6.36851856,0.79996510,0.05566536,0.61596975,-1.27437249,0.85780143,0.61003848];
    const dot = (coefficients) => coefficients.reduce((sum, coefficient, index) => sum + coefficient * x[index], 0);
    const lowExp = Math.exp(dot(lowCoefficients)), highExp = Math.exp(dot(highCoefficients)), denominator = 1 + lowExp + highExp;
    return { valid: true, noCancer: 100 / denominator, lowGrade: 100 * lowExp / denominator, highGrade: 100 * highExp / denominator };
  }

  function psaKinetics(measurements = []) {
    const rows = measurements.map((item) => ({ date: new Date(item.date), value: n(item.value, -1) }))
      .filter((item) => !Number.isNaN(item.date.getTime()) && item.value > 0)
      .sort((a, b) => a.date - b.date);
    if (rows.length < 2) return { count: rows.length, doublingTimeMonths: null, velocityPerYear: null };
    const origin = rows[0].date.getTime();
    const xs = rows.map((item) => (item.date.getTime() - origin) / 2629800000);
    const logs = rows.map((item) => Math.log(item.value));
    const values = rows.map((item) => item.value);
    const slope = linearSlope(xs, logs);
    const velocityMonth = linearSlope(xs, values);
    const gapsDays = rows.slice(1).map((item,index)=>(item.date.getTime()-rows[index].date.getTime())/86400000);
    const spanMonths = xs[xs.length-1]-xs[0];
    return {
      count: rows.length,
      doublingTimeMonths: slope > 0 ? Math.log(2) / slope : null,
      velocityPerYear: Number.isFinite(velocityMonth) ? velocityMonth * 12 : null,
      spanMonths,
      minimumGapDays: Math.min(...gapsDays),
      withinTwelveMonths: spanMonths <= 12.1,
      first: rows[0],
      last: rows[rows.length - 1],
    };
  }

  function linearSlope(xs, ys) {
    const meanX = xs.reduce((a, b) => a + b, 0) / xs.length;
    const meanY = ys.reduce((a, b) => a + b, 0) / ys.length;
    const denominator = xs.reduce((sum, x) => sum + Math.pow(x - meanX, 2), 0);
    if (!denominator) return Number.NaN;
    return xs.reduce((sum, x, index) => sum + (x - meanX) * (ys[index] - meanY), 0) / denominator;
  }

  function biochemicalRecurrence(input = {}) {
    const context = String(input.context || ""), psa = n(input.psa), nadir = n(input.nadir);
    if (context === "post_rt") return { met: psa >= nadir + 2, label: "Phoenix: PSA actual ≥ nadir + 2 ng/ml" };
    if (context === "post_rp") return { met: psa >= 0.2 && Boolean(input.confirmed), pendingConfirmation: psa >= 0.2 && !input.confirmed, label: "Post-RP: PSA ≥0,2 ng/ml confirmado" };
    return { met: false, label: "Sin criterio de recaida aplicable a este contexto" };
  }

  function metastaticProstate(input = {}) {
    const bone = n(input.bone);
    const chaartedHigh = Boolean(input.visceral) || (bone >= 4 && Boolean(input.outsideAxial));
    const latitudeFactors = (input.visceral ? 1 : 0) + (input.gleasonHigh ? 1 : 0) + (bone >= 3 ? 1 : 0);
    return { chaartedHigh, latitudeFactors, latitudeHigh: latitudeFactors >= 2 };
  }

  function eortcNmibc(input = {}) {
    const has = (key) => Object.prototype.hasOwnProperty.call(input, key);
    const valid = has("number") && [0,3,6].includes(Number(input.number))
      && has("size") && [0,3].includes(Number(input.size))
      && has("prior") && [0,2,4].includes(Number(input.prior))
      && has("grade") && [0,1,2].includes(Number(input.grade))
      && typeof input.t1 === "boolean" && typeof input.cis === "boolean";
    if (!valid) return { valid: false, reason: "Faltan variables EORTC o contienen categorías no válidas" };
    const number = n(input.number), size = n(input.size), prior = n(input.prior), grade = n(input.grade);
    const recurrenceScore = number + size + prior + (input.t1 ? 1 : 0) + (input.cis ? 1 : 0) + grade;
    const progressionScore = (number > 0 ? 3 : 0) + size + (prior > 0 ? 2 : 0) + (input.t1 ? 4 : 0) + (input.cis ? 6 : 0) + (grade === 2 ? 5 : 0);
    const recurrence = recurrenceScore === 0 ? [15,31] : recurrenceScore <= 4 ? [24,46] : recurrenceScore <= 9 ? [38,62] : [61,78];
    const progression = progressionScore === 0 ? [0.2,0.8] : progressionScore <= 6 ? [1,6] : progressionScore <= 13 ? [5,17] : [17,45];
    return { valid: true, recurrenceScore, progressionScore, recurrence1y:recurrence[0], recurrence5y:recurrence[1], progression1y:progression[0], progression5y:progression[1] };
  }

  function eauNmibc(input = {}) {
    const system=String(input.system||"who2004"), stage=String(input.stage||"ta"), grade=String(input.grade||"low"), cis=Boolean(input.cis), primary=Boolean(input.primary);
    const gradeValid=system==="who2004"?["low","high"].includes(grade):system==="who1973"?["g1","g2","g3"].includes(grade):false;
    if(!["who2004","who1973"].includes(system)||!["ta","t1"].includes(stage)||!gradeValid||!Number.isFinite(Number(input.age))||n(input.age)<=0||!Number.isFinite(Number(input.size))||n(input.size)<0||typeof input.multiple!=="boolean"||typeof input.primary!=="boolean")return {valid:false,reason:"Completar sistema de grado, grado compatible, presentación, edad, tamaño, focalidad y estadio"};
    const factors=(n(input.age)>70?1:0)+(input.multiple?1:0)+(n(input.size)>=3?1:0);
    if(input.lvi||input.prostaticCis||input.variant)return {valid:true,group:"muy alto",factors,probabilities:null,special:true};
    if(input.pureCis)return {valid:true,group:"alto",factors,probabilities:null,special:true};
    let group="intermedio";
    if(system==="who1973"){
      const g1=grade==="g1",g2=grade==="g2",g3=grade==="g3";
      const veryHigh=(stage==="ta"&&g3&&cis&&factors===3)||(stage==="t1"&&g2&&cis&&factors>=2)||(stage==="t1"&&g3&&cis&&factors>=1)||(stage==="t1"&&g3&&!cis&&factors===3);
      const high=(stage==="t1"&&g3&&!cis)||cis||(stage==="ta"&&g2&&!cis&&factors===3)||(stage==="t1"&&g1&&!cis&&factors===3)||(stage==="ta"&&g3&&!cis&&factors>=2)||(stage==="t1"&&g2&&!cis&&factors>=1);
      const low=primary&&!cis&&g1&&((!input.multiple&&n(input.size)<3&&n(input.age)<=70)||(stage==="ta"&&factors<=1));
      group=veryHigh?"muy alto":high?"alto":low?"bajo":"intermedio";
    }else{
      const lowGrade=grade==="low",highGrade=grade==="high";
      const veryHigh=(stage==="ta"&&highGrade&&cis&&factors===3)||(stage==="t1"&&highGrade&&cis&&factors>=1)||(stage==="t1"&&highGrade&&!cis&&factors===3);
      const high=(stage==="t1"&&highGrade&&!cis)||cis||(stage==="ta"&&lowGrade&&!cis&&factors===3)||((stage==="ta"&&highGrade)||(stage==="t1"&&lowGrade))&&!cis&&factors>=2;
      const low=primary&&!cis&&lowGrade&&((!input.multiple&&n(input.size)<3&&n(input.age)<=70)||(stage==="ta"&&factors<=1));
      group=veryHigh?"muy alto":high?"alto":low?"bajo":"intermedio";
    }
    if(!primary&&group==="bajo")group="intermedio";
    const tables={who2004:{"bajo":[0.06,0.93,3.7],"intermedio":[1,4.9,8.5],"alto":[3.5,9.6,14],"muy alto":[16,40,53]},who1973:{"bajo":[0.12,0.57,3],"intermedio":[0.65,3.6,7.4],"alto":[3.8,11,14],"muy alto":[20,44,59]}};
    return {valid:true,group,factors,probabilities:primary?tables[system][group]:null,special:false};
  }

  function cuetoNmibc(input={}){
    const hasSex=Object.prototype.hasOwnProperty.call(input,"female")||["female","male"].includes(String(input.sex));
    if(!hasSex||!Number.isFinite(Number(input.age))||n(input.age)<=0||!["g1","g2","g3"].includes(String(input.grade))||typeof input.moreThanThree!=="boolean"||typeof input.recurrent!=="boolean"||typeof input.t1!=="boolean"||typeof input.cis!=="boolean")return {valid:false,reason:"Faltan variables CUETO o contienen categorías no válidas"};
    const female=String(input.sex)==="female"||input.female===true;
    const age=n(input.age),grade=String(input.grade||"g1");
    const recurrence=(female?3:0)+(age>70?2:age>=60?1:0)+(input.moreThanThree?2:0)+(input.recurrent?4:0)+(input.cis?2:0)+(grade==="g3"?3:grade==="g2"?1:0);
    const progression=(age>70?2:0)+(input.moreThanThree?1:0)+(input.recurrent?2:0)+(input.t1?2:0)+(input.cis?1:0)+(grade==="g3"?6:grade==="g2"?2:0);
    const risks=(score)=>score<=4?[8,21]:score<=6?[12,36]:score<=9?[25,48]:[42,68];
    const progRisks=(score)=>score<=4?[1,4]:score<=6?[3,12]:score<=9?[6,21]:[14,34];
    return {valid:true,recurrenceScore:recurrence,progressionScore:progression,recurrence:risks(recurrence),progression:progRisks(progression)};
  }

  function cisplatinEligibility(input = {}) {
    const reasons = [];
    const ecog=n(input.ecog),gfr=n(input.gfr);
    if(!Number.isFinite(Number(input.gfr))||gfr<=0)return {valid:false,eligible:false,platinumIneligible:false,reasons:["falta una medición válida de función renal"],borderlineRenal:false};
    if (ecog > 1) reasons.push("ECOG >1");
    if (gfr <= 60) reasons.push("GFR ≤60 ml/min");
    if (n(input.hearing) >= 2) reasons.push("hipoacusia audiometrica ≥G2");
    if (n(input.neuropathy) >= 2) reasons.push("neuropatia ≥G2");
    if (n(input.nyha) >= 3) reasons.push("insuficiencia cardiaca NYHA III/IV");
    if (input.severeComorbidity) reasons.push("comorbilidad severa >G2");
    const platinumIneligible=gfr<30||ecog>2||(ecog===2&&gfr<60)||Boolean(input.severeComorbidity);
    return { valid:true,eligible: reasons.length === 0 && !platinumIneligible, platinumIneligible, reasons, borderlineRenal: gfr >= 40 && gfr <= 60 };
  }

  function postCystectomy(input={}){
    if(input.metastatic)return {inScope:false,recommendations:["Enfermedad M1: fuera del módulo adyuvante post-cistectomía"]};
    if(input.mKnown===false)return {inScope:false,incomplete:true,recommendations:["Falta confirmar M0 antes de aplicar el módulo adyuvante"]};
    if(input.nodeKnown===false)return {inScope:false,incomplete:true,recommendations:["pNx: completar evaluación ganglionar y discutir en comité"]};
    const t=n(input.t),nodePositive=Boolean(input.nodePositive),afterNac=Boolean(input.nac);
    const highRiskAfterNac=afterNac&&(t>=2||nodePositive);
    const highRiskWithoutNac=!afterNac&&(t>=3||nodePositive);
    const recommendations=[];
    if(input.modernPerioperative)recommendations.push("Aplicar el protocolo perioperatorio moderno y evitar apilar adyuvancias automáticamente");
    else {
      if(highRiskWithoutNac&&input.cisplatinEligible&&!input.cisplatinDeclined)recommendations.push("Ofrecer quimioterapia adyuvante combinada basada en cisplatino");
      if(highRiskAfterNac||(highRiskWithoutNac&&((input.cisplatinEligible===false)||input.cisplatinDeclined)))recommendations.push("Evaluar nivolumab adyuvante en comité multidisciplinario");
      if(highRiskWithoutNac&&input.cisplatinEligible===undefined&&!input.cisplatinDeclined)recommendations.push("Falta definir aptitud para cisplatino antes de seleccionar adyuvancia");
    }
    if(t>=3.5||nodePositive||input.marginPositive)recommendations.push("Considerar radioterapia adyuvante para control locorregional; sin beneficio demostrado en supervivencia global");
    if(!recommendations.length)recommendations.push("No cumple un disparador adyuvante EAU por estadio con los datos ingresados");
    return {inScope:true,highRiskAfterNac,highRiskWithoutNac,recommendations};
  }

  function utucRisk(input = {}) {
    if (input.metastatic) return { key: "out_of_scope", label: "Fuera de alcance: enfermedad metastásica", strong: [], weak: [] };
    const cytology = String(input.cytology || (input.highCytology ? "high" : "missing"));
    const biopsy = String(input.biopsy || "missing");
    const ctAssessment = String(input.ctAssessment || (input.invasive ? "invasive" : "missing"));
    const focality=String(input.focality||"");
    const strong = [];
    if (cytology === "high") strong.push("citología de alto grado");
    if (biopsy === "high") strong.push("biopsia de alto grado");
    if (ctAssessment === "invasive") strong.push("invasión local en TC");
    if (input.variant) strong.push("variante histologica agresiva");
    const weak = [];
    if (n(input.size) >= 2) weak.push("tamaño ≥2 cm");
    if (focality==="multifocal"||input.multifocal) weak.push("multifocalidad");
    if (input.hydronephrosis) weak.push("hidroureteronefrosis");
    if (strong.length) return { key: "high", label: "Alto riesgo: criterio fuerte", strong, weak };
    const missing = [];
    if(!Number.isFinite(Number(input.size))||n(input.size)<=0)missing.push("tamaño tumoral");
    if(!["unifocal","multifocal"].includes(focality)&&typeof input.multifocal!=="boolean")missing.push("focalidad");
    if(!["negative","high","missing"].includes(cytology))missing.push("citología válida");
    if(!["low","high","nondiagnostic","missing"].includes(biopsy))missing.push("biopsia válida");
    if(!["noninvasive","invasive","missing"].includes(ctAssessment))missing.push("evaluación de TC válida");
    if (cytology === "missing") missing.push("citología");
    if (biopsy === "missing" || biopsy === "nondiagnostic") missing.push("biopsia low-grade confiable");
    if (ctAssessment === "missing") missing.push("evaluación de invasión en TC");
    if (missing.length) return { key: "uncertain", label: "Información insuficiente para clasificar", strong, weak, missing };
    if (!weak.length) return { key: "low", label: "Bajo riesgo probable", strong, weak };
    return { key: "low_with_weak", label: "Sin criterio fuerte; sólo factores débiles", strong, weak };
  }

  function renalNephrometry(input = {}) {
    const size = n(input.size);
    const radius = size <= 4 ? 1 : size < 7 ? 2 : 3;
    const total = radius + n(input.exophytic) + n(input.nearness) + n(input.location);
    const complexity = total <= 6 ? "baja" : total <= 9 ? "moderada" : "alta";
    const ap = ["a", "p", "x"].includes(String(input.anteriorPosterior)) ? String(input.anteriorPosterior) : "x";
    return { total, radius, complexity, suffix: `${ap}${input.hilar ? "h" : ""}` };
  }

  function paduaNephrometry(input = {}) {
    const size = n(input.size);
    const sizePoints = size <= 4 ? 1 : size <= 7 ? 2 : 3;
    const total = n(input.longitudinal) + n(input.exophytic) + n(input.rim) + n(input.sinus) + n(input.collecting) + sizePoints;
    const complexity = total <= 7 ? "baja" : total <= 9 ? "moderada" : "alta";
    const ap = ["a", "p", "x"].includes(String(input.anteriorPosterior)) ? String(input.anteriorPosterior) : "x";
    return { total, sizePoints, complexity, suffix: ap };
  }

  function leibovich2003(input = {}) {
    const pt = String(input.pt || "").toLowerCase();
    const tPoints = pt === "pt1a" ? 0 : pt === "pt1b" ? 2 : pt.startsWith("pt2") ? 3 : (pt.startsWith("pt3") || pt.startsWith("pt4")) ? 4 : 0;
    const grade = n(input.grade);
    const gradePoints = grade >= 4 ? 3 : grade === 3 ? 1 : 0;
    const total = tPoints + (input.nodePositive ? 2 : 0) + (n(input.size) >= 10 ? 1 : 0) + gradePoints + (input.necrosis ? 1 : 0);
    return { total, category: total <= 2 ? "bajo" : total <= 5 ? "intermedio" : "alto" };
  }

  function uissLocalized(input = {}) {
    if (input.nodePositive || input.metastatic) return { key: "not_localized", label: "No corresponde al UISS localizado" };
    const pt = String(input.pt || "").toLowerCase(), grade = n(input.grade), ecog = n(input.ecog);
    if (pt.startsWith("pt1") && grade <= 2 && ecog === 0) return { key: "low", label: "Bajo riesgo" };
    if ((pt.startsWith("pt3") && grade >= 2 && ecog >= 1) || pt.startsWith("pt4")) return { key: "high", label: "Alto riesgo" };
    return { key: "intermediate", label: "Riesgo intermedio" };
  }

  function imdc(input = {}) {
    const keys = ["kps", "time", "hb", "calcium", "neutrophils", "platelets"];
    const total = keys.reduce((sum, key) => sum + (input[key] ? 1 : 0), 0);
    return { total, category: total === 0 ? "favorable" : total <= 2 ? "intermedio" : "pobre" };
  }

  function igcccg(input = {}) {
    const histology = String(input.histology || ""), afp = n(input.afp), afpUpperLimit = n(input.afpUpperLimit, 10), hcg = n(input.hcg), ldh = n(input.ldhRatio), primary = String(input.primary || "testis");
    if (histology === "seminoma" && afp > afpUpperLimit) return { valid: false, label: "No clasificable como seminoma puro: AFP elevada" };
    const markerGroup = afp > 10000 || hcg > 50000 || ldh > 10 ? "S3" : afp >= 1000 || hcg >= 5000 || ldh >= 1.5 ? "S2" : "S1";
    if (histology === "seminoma") {
      const category = input.nonPulmonary ? "intermedio" : "bueno";
      return { valid: true, markerGroup, category, label: category === "bueno" ? "buen pronóstico" : "pronóstico intermedio", ldhWarning: !input.nonPulmonary && ldh > 2.5, pfs5y: category === "bueno" ? 89 : 79, os5y: category === "bueno" ? 95 : 88 };
    }
    const favorablePrimary = primary === "testis" || primary === "retroperitoneal";
    const poor = primary === "mediastinal" || input.nonPulmonary || markerGroup === "S3";
    if (!poor && !favorablePrimary) return { valid: false, markerGroup, label: "Sitio primario fuera de la clasificación clásica IGCCCG" };
    const category = poor ? "desfavorable" : markerGroup === "S2" ? "intermedio" : "bueno";
    const outcomes = category === "bueno" ? [90, 96] : category === "intermedio" ? [78, 89] : [54, 67];
    return { valid: true, markerGroup, category, label: category === "bueno" ? "buen pronóstico" : category === "intermedio" ? "pronóstico intermedio" : "pronóstico desfavorable", pfs5y: outcomes[0], os5y: outcomes[1] };
  }

  return {
    bodySurfaceArea, bodyMassIndex, calvertDose, charlson, g8, carg, ipss, shim,
    eauProstateRisk, capra, capraS, psaDensity, pbcg, psaKinetics, biochemicalRecurrence,
    metastaticProstate, eortcNmibc, eauNmibc, cuetoNmibc, cisplatinEligibility, postCystectomy, utucRisk, renalNephrometry, paduaNephrometry,
    leibovich2003, uissLocalized, imdc, igcccg,
  };
});
