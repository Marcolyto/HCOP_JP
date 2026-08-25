# Fuentes, versiones y avisos de terceros

Revisión documental: **11-07-2026**.

Este archivo identifica la referencia clínica o publicación de origen de las 57 herramientas: 23 originales, 30 incorporadas en la ampliación oncológica 2026 y 4 nuevas calculadoras de radioterapia. Las páginas vivas se consignan con su fecha de consulta; los modelos históricos conservan el año de su cohorte/publicación y no se presentan como evidencia actualizada. Las denominaciones, publicaciones, cuestionarios y guías pertenecen a sus respectivos titulares. Su cita no implica aval ni concede derechos de redistribución.

| # | ID | Herramienta | Fuente y versión de referencia |
|---:|---|---|---|
| 1 | `bsa` | Superficie corporal — Mosteller | [Mosteller, 1987](https://pubmed.ncbi.nlm.nih.gov/3657876/). |
| 2 | `bmi` | Índice de masa corporal | [OMS: obesidad y sobrepeso](https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight), página viva consultada el 11-07-2026. |
| 3 | `calvert` | Carboplatino — fórmula de Calvert | [Calvert et al., 1989](https://pubmed.ncbi.nlm.nih.gov/2681557/). La fórmula original usa GFR absoluta. |
| 4 | `ecog` | ECOG / Karnofsky | [ECOG-ACRIN Performance Status](https://ecog-acrin.org/resources/ecog-performance-status/) y [NCI: Karnofsky Performance Status](https://www.cancer.gov/publications/dictionaries/cancer-terms/def/karnofsky-performance-status), páginas vivas consultadas el 11-07-2026. |
| 5 | `charlson` | Charlson comorbidity index | Índice original: [Charlson et al., 1987](https://pubmed.ncbi.nlm.nih.gov/3558716/). Ajuste combinado por edad: [Charlson et al., 1994](https://pubmed.ncbi.nlm.nih.gov/7722560/). |
| 6 | `g8-carg` | G8 / CARG | G8: [ONCODAGE, 2014](https://pubmed.ncbi.nlm.nih.gov/25503576/). CARG: [Hurria et al., 2011](https://pubmed.ncbi.nlm.nih.gov/21810685/) y [recurso oficial CARG](https://mycarg.org/resources-activities/tools/select-tool/ctcl/). Son escalas separadas. |
| 7 | `ipss-shim` | IPSS / SHIM | IPSS/AUA-7: [Barry et al., 1992](https://pubmed.ncbi.nlm.nih.gov/1279218/). IIEF-5/SHIM: [Rosen et al., 1999](https://doi.org/10.1038/sj.ijir.3900472). Son cuestionarios separados. |
| 8 | `damico` | EAU 2026 — riesgo prostático | [EAU Prostate Cancer Guidelines 2026, clasificación y estadificación, tabla 4.3](https://uroweb.org/guidelines/prostate-cancer/chapter/classification-and-staging-systems), página viva consultada el 11-07-2026. |
| 9 | `capra` | CAPRA / CAPRA-S | [UCSF CAPRA](https://urology.ucsf.edu/research/cancer/prostate-cancer-risk-assessment-and-the-ucsf-capra-score) y [CAPRA-S, 2011](https://pmc.ncbi.nlm.nih.gov/articles/PMC3170662/). Son modelos preoperatorio y posoperatorio distintos. |
| 10 | `partin` | Partin tables | [Johns Hopkins Partin Tables](https://www.hopkinsmedicine.org/brady-urology-institute/conditions-and-treatments/prostate-cancer/risk-assessment-tools/partin-tables), actualización contemporánea publicada en 2017 con cohorte 2010-2015; página consultada el 11-07-2026. |
| 11 | `nodal-risk` | Roach nodal / Briganti | Fórmula local Roach: [Roach et al., 1994](https://pubmed.ncbi.nlm.nih.gov/7505775/), modelo histórico con limitaciones contemporáneas. Briganti se referencia como modelo distinto: [nomograma 2012](https://pubmed.ncbi.nlm.nih.gov/22078338/); no debe sustituirse por la fórmula de Roach. |
| 12 | `mskcc-prostate` | Nomogramas MSKCC próstata | [MSKCC Prostate Cancer Nomograms](https://www.mskcc.org/nomograms/prostate), calculadoras institucionales dinámicas consultadas el 11-07-2026. Los resultados oficiales se obtienen en MSKCC; HCOP no presume una reimplementación local equivalente. |
| 13 | `biopsy-risk` | PBCG — riesgo antes de biopsia | [Ankerst et al., 2018](https://pmc.ncbi.nlm.nih.gov/articles/PMC6082177/), [calculadora oficial](https://riskcalc.org/PBCG/) y [código R público](https://github.com/ClevelandClinicQHS/riskcalc-website/blob/main/PBCG/R_code_PBCG_risk_calculator.R), consultados el 11-07-2026. Véase el aviso de licencia específico más abajo. |
| 14 | `psa-kinetics` | PSA-D / PSA doubling time / BCR | Cinética: [MSKCC PSA Doubling Time](https://www.mskcc.org/nomograms/prostate/psa_doubling_time). Recurrencia tras radioterapia: [consenso Phoenix, 2006](https://pubmed.ncbi.nlm.nih.gov/16798415/). Recurrencia posprostatectomía: [AUA/ASTRO/SUO Salvage Therapy Guideline 2024](https://www.auanet.org/documents/Guidelines/PDF/2024%20Guidelines/STPC%20Unabridged%20Final.pdf). |
| 15 | `chaarted-latitude` | CHAARTED / LATITUDE | CHAARTED: [Kyriakopoulos et al., 2018](https://pubmed.ncbi.nlm.nih.gov/29384722/). LATITUDE: [Fizazi et al., 2019](https://pubmed.ncbi.nlm.nih.gov/30987939/). Contexto terapéutico: [EAU Prostate Cancer Guidelines 2026](https://uroweb.org/guidelines/prostate-cancer/chapter/treatment), consultada el 11-07-2026. |
| 16 | `nmibc` | NMIBC EAU / EORTC / CUETO | [EAU NMIBC 2026](https://uroweb.org/guidelines/non-muscle-invasive-bladder-cancer/chapter/predicting-disease-recurrence-and-progression), página viva consultada el 11-07-2026; [EORTC 2006](https://pubmed.ncbi.nlm.nih.gov/16442208/); [CUETO 2009](https://doi.org/10.1016/j.juro.2009.07.016). Son modelos de poblaciones distintas y no se combinan. |
| 17 | `cystectomy` | Post-cistectomía | [EAU Muscle-invasive and Metastatic Bladder Cancer Guidelines 2026](https://uroweb.org/guidelines/muscle-invasive-and-metastatic-bladder-cancer/chapter/disease-management), página viva consultada el 11-07-2026. |
| 18 | `cisplatin` | Aptitud para cisplatino y platinum | Criterios de consenso: [Galsky et al., 2011](https://pubmed.ncbi.nlm.nih.gov/21555688/). Contexto clínico: [EAU MIBC 2026](https://uroweb.org/guidelines/muscle-invasive-and-metastatic-bladder-cancer/chapter/disease-management), consultada el 11-07-2026. |
| 19 | `utuc` | UTUC — riesgo EAU 2026 | [EAU Upper Urinary Tract Urothelial Carcinoma Guidelines 2026 — risk stratification](https://uroweb.org/guidelines/upper-urinary-tract-urothelial-cell-carcinoma/chapter/risk-stratification), página viva consultada el 11-07-2026. |
| 20 | `renal-complexity` | RENAL / PADUA | RENAL: [Kutikov y Uzzo, 2009](https://pubmed.ncbi.nlm.nih.gov/19616235/). PADUA: [Ficarra et al., 2009](https://pubmed.ncbi.nlm.nih.gov/19665284/). Son sistemas anatómicos distintos. |
| 21 | `leibovich` | Leibovich 2003 / UISS localizado | [Leibovich 2003](https://pubmed.ncbi.nlm.nih.gov/12655523/) y [UISS 2001](https://doi.org/10.1200/JCO.2001.19.6.1649). Se implementan como modelos separados. SSIGN no se calcula porque su TNM histórico no debe mezclarse con TNM moderno. Contexto: [EAU RCC 2026](https://uroweb.org/guidelines/renal-cell-carcinoma/chapter/prognostic-factors), consultada el 11-07-2026. |
| 22 | `imdc` | IMDC — carcinoma renal metastásico | IMDC: [Heng et al., 2009](https://pubmed.ncbi.nlm.nih.gov/19826129/) y [sitio oficial IMDC](https://www.imdconline.com/). El modelo MSKCC histórico no se calcula ni se combina. Contexto: [EAU RCC 2026](https://uroweb.org/guidelines/renal-cell-carcinoma/chapter/prognostic-factors), consultada el 11-07-2026. |
| 23 | `igcccg` | IGCCCG testículo | Clasificación original: [IGCCCG, 1997](https://pubmed.ncbi.nlm.nih.gov/9053482/). Actualización: [no seminoma, 2021; corrección 2022](https://ascopubs.org/doi/10.1200/JCO.20.03296) y [seminoma, 2021](https://pubmed.ncbi.nlm.nih.gov/33729863/). Contexto: [EAU Testicular Cancer Guidelines 2026](https://uroweb.org/guidelines/testicular-cancer/chapter/staging-amp-classification-systems), consultada el 11-07-2026. |

## Ampliación oncológica 2026

Las siguientes 30 entradas se ejecutan con reglas locales reproducibles. Los puntos de corte se probaron de forma explícita y cada pantalla informa su población y sus límites; que un caso coincida con una cohorte o criterio no equivale a una indicación terapéutica.

| # | ID | Herramienta | Fuente y versión de referencia |
|---:|---|---|---|
| 24 | `renal-function-oncology` | Cockcroft–Gault / CKD-EPI 2021 | [Cockcroft y Gault, 1976](https://pubmed.ncbi.nlm.nih.gov/1244564/); ecuaciones sin raza [NKF](https://www.kidney.org/professionals/ckd-epi-creatinine-equation-2021) y [NIDDK](https://www.niddk.nih.gov/research-funding/research-programs/kidney-clinical-research-epidemiology/laboratory/glomerular-filtration-rate-equations/adults), consultadas el 11-07-2026. |
| 25 | `anc-ctcae-v6` | ANC / CTCAE v6 | [NCI CTCAE v6.0, publicado el 22-07-2025](https://dctd.cancer.gov/research/ctep-trials/for-sites/adverse-events/ctcae-v6.pdf) y [definición NCI de ANC](https://www.cancer.gov/publications/dictionaries/cancer-terms/def/anc). |
| 26 | `khorana-vte` | Khorana — VTE | [Modelo original abierto](https://pmc.ncbi.nlm.nih.gov/articles/PMC2384124/) y [guía ASH](https://pmc.ncbi.nlm.nih.gov/articles/PMC7903232/). Se muestran por separado la categoría histórica y el umbral moderno ≥2. |
| 27 | `mascc-febrile-neutropenia` | MASCC | [Estudio original](https://pubmed.ncbi.nlm.nih.gov/10944139/) y [guía ASCO/IDSA](https://www.idsociety.org/practice-guideline/fever-and-neutropenia-in-adults-with-cancer/). |
| 28 | `cisne-febrile-neutropenia` | CISNE | [Validación FINITE/CISNE](https://pubmed.ncbi.nlm.nih.gov/25559804/) y [guía ASCO/IDSA](https://www.idsociety.org/globalassets/idsa/practice-guidelines/outpatient-management-of-fever-and-neutropenia.pdf). |
| 29 | `palliative-prognostic-index` | PPI | [Morita et al., índice original](https://pubmed.ncbi.nlm.nih.gov/10335930/). Los cortes son pronósticos poblacionales, no fechas individuales. |
| 30 | `bed-eqd2` | BED y EQD2 del fraccionamiento | [IAEA Training Course Series 42](https://www-pub.iaea.org/MTCD/publications/PDF/TCS-42_web.pdf), [revisión con fórmulas](https://pmc.ncbi.nlm.nih.gov/articles/PMC6435084/) y [AAPM TG-314, 2025](https://aapm.onlinelibrary.wiley.com/doi/full/10.1002/mp.17502). |
| 31 | `qtc-fridericia` | QTc Fridericia | [ESC Cardio-Oncology](https://academic.oup.com/eurheartj/article/43/41/4229/6673995). El prospecto específico y la revisión clínica prevalecen. |
| 32 | `nottingham-prognostic-index` | NPI | [Nottingham Prognostic Index](https://pubmed.ncbi.nlm.nih.gov/1391987/). Modelo histórico, sin estimaciones locales de supervivencia. |
| 33 | `residual-cancer-burden-experimental` | RCB experimental | Método y control con la [calculadora oficial de MD Anderson](https://www3.mdanderson.org/app/medcalc/index.cfm?pagename=jsconvert3) y [publicación de validación](https://pmc.ncbi.nlm.nih.gov/articles/PMC2601022/). La implementación local se rotula experimental y siempre exige confirmación oficial. |
| 34 | `pepi-breast` | PEPI | [Ellis et al., P024/IMPACT](https://pubmed.ncbi.nlm.nih.gov/18812550/). Sólo después de endocrinoterapia neoadyuvante en la población correspondiente. |
| 35 | `cts5-breast` | CTS5 | [Desarrollo y validación](https://pmc.ncbi.nlm.nih.gov/articles/PMC6049399/) y [validación clínica](https://pubmed.ncbi.nlm.nih.gov/33222093/). El tamaño se limita a 30 mm como en el modelo. |
| 36 | `monarche-cohort-1` | monarchE cohorte 1 | Definición vigente de alto riesgo y retiro del requisito Ki-67: [FDA](https://www.fda.gov/drugs/resources-information-approved-drugs/fda-expands-early-breast-cancer-indication-abemaciclib-endocrine-therapy). |
| 37 | `olympia-cpseg` | OlympiA / CPS+EG | Criterios de los cuatro escenarios y tabla CPS+EG: [etiqueta FDA de olaparib](https://www.accessdata.fda.gov/drugsatfda_docs/label/2022/208558s023lbl.pdf). |
| 38 | `international-prognostic-index` | IPI | [International NHL Prognostic Factors Project](https://pubmed.ncbi.nlm.nih.gov/8141877/). No se extrapolan porcentajes históricos de supervivencia. |
| 39 | `r2-iss-myeloma` | R2-ISS | [European Myeloma Network/HARMONY, 2022](https://pubmed.ncbi.nlm.nih.gov/35605179/). |
| 40 | `gyne-sedlis` | Sedlis | [GOG-92](https://pubmed.ncbi.nlm.nih.gov/10329031/) y [VA Cervical Cancer Pathway 2025](https://www.cancer.va.gov/CANCER/assets/pdf/clinical-pathways/cervical.pdf). Se reproducen las cuatro combinaciones exactas, no una regla “dos de tres”. |
| 41 | `gyne-peters` | Peters | [GOG-109/Peters](https://pubmed.ncbi.nlm.nih.gov/10764420/) y [ESGO/ESTRO/ESP 2023](https://pmc.ncbi.nlm.nih.gov/articles/PMC10176411/). |
| 42 | `gyne-promise` | ProMisE / ESGO 2025 | [Desarrollo de ProMisE](https://pubmed.ncbi.nlm.nih.gov/26172027/), [validación](https://pubmed.ncbi.nlm.nih.gov/28061006/) y [ESGO/ESTRO/ESP Endometrial Cancer 2025](https://guidelines.esgo.org/media/2025/09/ESGO-ESTRO-ESP-Guidelines-for-EC_-LO-July-2025.pdf). |
| 43 | `gyne-rmi-i` | RMI I | [Modelo original](https://pubmed.ncbi.nlm.nih.gov/2223684/) y [NICE CG122, actualizado el 15-04-2026](https://www.nice.org.uk/guidance/cg122/chapter/Appendix-Risk-of-malignancy-index-RMI-I). |
| 44 | `gyne-fagotti` | Fagotti PIV clásico | [Modelo laparoscópico 2006](https://doi.org/10.1245/ASO.2006.08.021) y [definiciones/validación externa](https://pmc.ncbi.nlm.nih.gov/articles/PMC11130284/). No se interpreta como irresecabilidad automática. |
| 45 | `gyne-ago-desktop` | AGO / DESKTOP III | [DESKTOP III](https://www.nejm.org/doi/full/10.1056/NEJMoa2103294) y [consenso ESGO ovario 2025](https://www.esgo.org/media/2025/08/Pocket-Guidelines_Ovarian-cancer-consensus.pdf). |
| 46 | `thorax_brock` | Brock / PanCan | [McWilliams et al., 2013](https://pubmed.ncbi.nlm.nih.gov/24004118/). Modelo completo para nódulos de hasta 30 mm. |
| 47 | `thorax_mayo_herder` | Mayo-Herder | [Mayo/Swensen](https://pubmed.ncbi.nlm.nih.gov/9129544/) y [Herder con PET-FDG](https://pubmed.ncbi.nlm.nih.gov/16236914/). |
| 48 | `thorax_lung_gpa_2022` | Lung GPA 2022 | [Sperduto et al., 2022](https://pubmed.ncbi.nlm.nih.gov/35331827/). Hojas separadas para adenocarcinoma, NSCLC no adenocarcinoma y SCLC. |
| 49 | `thorax_lipi` | LIPI | [Mezquita et al., 2018](https://pubmed.ncbi.nlm.nih.gov/29327044/). Índice pronóstico; no se usa como selector aislado de inmunoterapia. |
| 50 | `digestive_albi` | ALBI / mALBI | [Johnson et al., 2015](https://pmc.ncbi.nlm.nih.gov/articles/PMC4322258/). Conversión explícita de unidades y subdivisión mALBI 2a/2b. |
| 51 | `digestive_french_afp_hcc` | AFP francés — HCC | Modelo Duvoux descrito y validado en la [revisión de criterios de trasplante](https://pmc.ncbi.nlm.nih.gov/articles/PMC4698488/) y su [comparación en práctica real](https://pmc.ncbi.nlm.nih.gov/articles/PMC8160826/). |
| 52 | `digestive_game` | GAME — metástasis hepáticas colorrectales | [Modelo GAME original](https://pmc.ncbi.nlm.nih.gov/articles/PMC7988484/). |
| 53 | `digestive_pci` | Peritoneal Cancer Index | Definición de 13 regiones y LS0–LS3: [descripción reproducible](https://pmc.ncbi.nlm.nih.gov/articles/PMC3280941/). No se aplica un corte universal de operabilidad. |

## Radioterapia

La categoría adapta las diez calculadoras no incrementales y no braquiterápicas del menú **Cálculo** de [`Marcolyto/pangeasystem`](https://github.com/Marcolyto/pangeasystem). Las páginas duplicadas por magnitud se agruparon en cinco pantallas: la herramienta BED/EQD2 ya existente y cuatro entradas nuevas. El código PHP, sus tablas tisulares y su búsqueda por fuerza bruta no se copiaron; las fórmulas se reimplementaron como reglas JavaScript determinísticas y probadas.

| # | ID | Herramienta | Fuente y versión de referencia |
|---:|---|---|---|
| 54 | `rt-dose-per-fraction-target` | Dosis por fracción desde BED o EQD2 | Inversión algebraica del modelo LQ, validada contra [IAEA TCS-42](https://www-pub.iaea.org/MTCD/publications/PDF/TCS-42_web.pdf) y [AAPM TG-314, 2025](https://aapm.onlinelibrary.wiley.com/doi/full/10.1002/mp.17502). |
| 55 | `rt-fractions-target` | Número de fracciones desde BED o EQD2 | Mismas relaciones LQ; cuando el resultado no es entero se recalculan ambos enteros vecinos en vez de redondear silenciosamente. |
| 56 | `rt-simultaneous-2-volumes` | Fraccionamiento simultáneo · 2 volúmenes | Enumeración de fracciones comunes y cálculo BED/EQD2 por volumen. Contexto del método SIB: [IsoBED](https://pmc.ncbi.nlm.nih.gov/articles/PMC3117739/). |
| 57 | `rt-simultaneous-3-volumes` | Fraccionamiento simultáneo · 3 volúmenes | Mismo motor para tres niveles de dosis. Cada volumen conserva su dosis física, BED y EQD2 por separado; no se suman volúmenes anatómicos distintos. |

Quedaron expresamente fuera `BED_incremental.php`, los dos moldes de braquiterapia y `compensar_dosis.php`. Esta última no forma parte del menú original y mezcla EQD2 con dosis física, por lo que no es dimensionalmente válida. Tampoco se reutilizó la tabla tisular histórica: contiene inconsistencias internas y presenta valores α/β inciertos como si fueran universales.

El modelo LQ básico supone reparación completa entre fracciones y no incorpora tiempo total, repoblación, recuperación tisular, heterogeneidad espacial ni reirradiación. La [RCR](https://www.rcr.ac.uk/media/z5jgmrhd/rcr-publications_the-timely-delivery-of-radical-radiotherapy-guidelines-for-the-management-of-unscheduled-treatment-interruptions-4th-edition_january-2019.pdf) exige protocolos y revisión especializada cuando se modifican esquemas; estas calculadoras son apoyo matemático y no prescripción.

### Aviso específico sobre RCB experimental

La fórmula RCB se ofrece únicamente para experimentación local y exige confirmación con la calculadora oficial. El método tiene antecedentes de protección por patente en algunas jurisdicciones; no se concede licencia de patente ni se presume aptitud para redistribución o uso comercial. Los derechos permanecen con sus titulares.

## PBCG: atribución y licencia

La implementación local de `biopsy-risk` adapta los coeficientes publicados para PBCG a partir del código R de **Cleveland Clinic Quantitative Health Sciences**, repositorio [`ClevelandClinicQHS/riskcalc-website`](https://github.com/ClevelandClinicQHS/riskcalc-website), y de Ankerst et al. (2018). Los derechos del material upstream permanecen con sus autores y licenciante.

El repositorio upstream declara `riskcalc.org` bajo la **[PolyForm Noncommercial License 1.0.0](https://polyformproject.org/licenses/noncommercial/1.0.0/)**. En consecuencia, el componente derivado de PBCG:

- puede utilizarse, modificarse y distribuirse sólo para fines permitidos por esa licencia, incluidos fines no comerciales;
- debe conservar una copia de los términos o el enlace oficial anterior cuando se distribuya;
- no concede uso comercial sin permiso previo del licenciante; y
- se entrega sin garantía ni responsabilidad del licenciante, en la medida permitida por la ley.

Esta condición se aplica al material derivado de PBCG y **no convierte automáticamente a todo HCOP ni a las otras 22 herramientas en software bajo PolyForm**. El README upstream además limita sus calculadoras a investigación y educación, sin autorización para uso clínico directo salvo validación y aprobación separadas.

## Alcance clínico

Estas herramientas apoyan documentación, docencia y revisión clínica; no sustituyen juicio profesional, guías locales, validación institucional ni calculadoras oficiales. Antes de una decisión asistencial deben confirmarse la población de derivación, las variables obligatorias, las unidades, la versión vigente y la aplicabilidad al paciente.
