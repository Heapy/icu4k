// © 2016 and later: Unicode, Inc. and others.
// Copyright (C) 2004-2014, International Business Machines Corporation and others.
// SPDX-License-Identifier: Unicode-3.0
package io.github.heapy.icu4k.impl

import io.github.heapy.icu4k.impl.data.ZoneInfoResourceData
import io.github.heapy.icu4k.impl.data.MetaZonesResourceData
import io.github.heapy.icu4k.impl.data.WindowsZonesResourceData
import io.github.heapy.icu4k.impl.data.ZoneResourceRegistry
import io.github.heapy.icu4k.impl.data.RegionResourceRegistry
import io.github.heapy.icu4k.impl.data.CoreResourceRegistry
import io.github.heapy.icu4k.impl.data.UnitResourceRegistry
import io.github.heapy.icu4k.impl.data.UcaData
import io.github.heapy.icu4k.impl.data.CollationAfResourceData
import io.github.heapy.icu4k.impl.data.CollationAmResourceData
import io.github.heapy.icu4k.impl.data.CollationArResourceData
import io.github.heapy.icu4k.impl.data.CollationArSAResourceData
import io.github.heapy.icu4k.impl.data.CollationArsResourceData
import io.github.heapy.icu4k.impl.data.CollationAsResourceData
import io.github.heapy.icu4k.impl.data.CollationAzResourceData
import io.github.heapy.icu4k.impl.data.CollationBeResourceData
import io.github.heapy.icu4k.impl.data.CollationBgResourceData
import io.github.heapy.icu4k.impl.data.CollationBloResourceData
import io.github.heapy.icu4k.impl.data.CollationBnResourceData
import io.github.heapy.icu4k.impl.data.CollationBoResourceData
import io.github.heapy.icu4k.impl.data.CollationBrResourceData
import io.github.heapy.icu4k.impl.data.CollationBsResourceData
import io.github.heapy.icu4k.impl.data.CollationBsCyrlResourceData
import io.github.heapy.icu4k.impl.data.CollationCaResourceData
import io.github.heapy.icu4k.impl.data.CollationCebResourceData
import io.github.heapy.icu4k.impl.data.CollationChrResourceData
import io.github.heapy.icu4k.impl.data.CollationCsResourceData
import io.github.heapy.icu4k.impl.data.CollationCyResourceData
import io.github.heapy.icu4k.impl.data.CollationDaResourceData
import io.github.heapy.icu4k.impl.data.CollationDeResourceData
import io.github.heapy.icu4k.impl.data.CollationDe_ResourceData
import io.github.heapy.icu4k.impl.data.CollationDeATResourceData
import io.github.heapy.icu4k.impl.data.CollationDe_PHONEBOOKResourceData
import io.github.heapy.icu4k.impl.data.CollationDsbResourceData
import io.github.heapy.icu4k.impl.data.CollationDzResourceData
import io.github.heapy.icu4k.impl.data.CollationEeResourceData
import io.github.heapy.icu4k.impl.data.CollationElResourceData
import io.github.heapy.icu4k.impl.data.CollationEnResourceData
import io.github.heapy.icu4k.impl.data.CollationEnUSResourceData
import io.github.heapy.icu4k.impl.data.CollationEnUSPOSIXResourceData
import io.github.heapy.icu4k.impl.data.CollationEoResourceData
import io.github.heapy.icu4k.impl.data.CollationEsResourceData
import io.github.heapy.icu4k.impl.data.CollationEs_ResourceData
import io.github.heapy.icu4k.impl.data.CollationEs_TRADITIONALResourceData
import io.github.heapy.icu4k.impl.data.CollationEtResourceData
import io.github.heapy.icu4k.impl.data.CollationFaResourceData
import io.github.heapy.icu4k.impl.data.CollationFaAFResourceData
import io.github.heapy.icu4k.impl.data.CollationFfResourceData
import io.github.heapy.icu4k.impl.data.CollationFfAdlmResourceData
import io.github.heapy.icu4k.impl.data.CollationFiResourceData
import io.github.heapy.icu4k.impl.data.CollationFilResourceData
import io.github.heapy.icu4k.impl.data.CollationFoResourceData
import io.github.heapy.icu4k.impl.data.CollationFrResourceData
import io.github.heapy.icu4k.impl.data.CollationFrCAResourceData
import io.github.heapy.icu4k.impl.data.CollationFyResourceData
import io.github.heapy.icu4k.impl.data.CollationGaResourceData
import io.github.heapy.icu4k.impl.data.CollationGlResourceData
import io.github.heapy.icu4k.impl.data.CollationGuResourceData
import io.github.heapy.icu4k.impl.data.CollationHaResourceData
import io.github.heapy.icu4k.impl.data.CollationHawResourceData
import io.github.heapy.icu4k.impl.data.CollationHeResourceData
import io.github.heapy.icu4k.impl.data.CollationHeILResourceData
import io.github.heapy.icu4k.impl.data.CollationHiResourceData
import io.github.heapy.icu4k.impl.data.CollationHrResourceData
import io.github.heapy.icu4k.impl.data.CollationHsbResourceData
import io.github.heapy.icu4k.impl.data.CollationHuResourceData
import io.github.heapy.icu4k.impl.data.CollationHyResourceData
import io.github.heapy.icu4k.impl.data.CollationIdResourceData
import io.github.heapy.icu4k.impl.data.CollationIdIDResourceData
import io.github.heapy.icu4k.impl.data.CollationIgResourceData
import io.github.heapy.icu4k.impl.data.CollationInResourceData
import io.github.heapy.icu4k.impl.data.CollationInIDResourceData
import io.github.heapy.icu4k.impl.data.CollationIsResourceData
import io.github.heapy.icu4k.impl.data.CollationItResourceData
import io.github.heapy.icu4k.impl.data.CollationIwResourceData
import io.github.heapy.icu4k.impl.data.CollationIwILResourceData
import io.github.heapy.icu4k.impl.data.CollationJaResourceData
import io.github.heapy.icu4k.impl.data.CollationKaResourceData
import io.github.heapy.icu4k.impl.data.CollationKkResourceData
import io.github.heapy.icu4k.impl.data.CollationKkArabResourceData
import io.github.heapy.icu4k.impl.data.CollationKlResourceData
import io.github.heapy.icu4k.impl.data.CollationKmResourceData
import io.github.heapy.icu4k.impl.data.CollationKnResourceData
import io.github.heapy.icu4k.impl.data.CollationKoResourceData
import io.github.heapy.icu4k.impl.data.CollationKokResourceData
import io.github.heapy.icu4k.impl.data.CollationKuResourceData
import io.github.heapy.icu4k.impl.data.CollationKyResourceData
import io.github.heapy.icu4k.impl.data.CollationLbResourceData
import io.github.heapy.icu4k.impl.data.CollationLijResourceData
import io.github.heapy.icu4k.impl.data.CollationLktResourceData
import io.github.heapy.icu4k.impl.data.CollationLnResourceData
import io.github.heapy.icu4k.impl.data.CollationLoResourceData
import io.github.heapy.icu4k.impl.data.CollationLtResourceData
import io.github.heapy.icu4k.impl.data.CollationLvResourceData
import io.github.heapy.icu4k.impl.data.CollationMkResourceData
import io.github.heapy.icu4k.impl.data.CollationMlResourceData
import io.github.heapy.icu4k.impl.data.CollationMnResourceData
import io.github.heapy.icu4k.impl.data.CollationMoResourceData
import io.github.heapy.icu4k.impl.data.CollationMrResourceData
import io.github.heapy.icu4k.impl.data.CollationMsResourceData
import io.github.heapy.icu4k.impl.data.CollationMtResourceData
import io.github.heapy.icu4k.impl.data.CollationMyResourceData
import io.github.heapy.icu4k.impl.data.CollationNbResourceData
import io.github.heapy.icu4k.impl.data.CollationNbNOResourceData
import io.github.heapy.icu4k.impl.data.CollationNeResourceData
import io.github.heapy.icu4k.impl.data.CollationNlResourceData
import io.github.heapy.icu4k.impl.data.CollationNnResourceData
import io.github.heapy.icu4k.impl.data.CollationNoResourceData
import io.github.heapy.icu4k.impl.data.CollationNoNOResourceData
import io.github.heapy.icu4k.impl.data.CollationNsoResourceData
import io.github.heapy.icu4k.impl.data.CollationOmResourceData
import io.github.heapy.icu4k.impl.data.CollationOrResourceData
import io.github.heapy.icu4k.impl.data.CollationPaResourceData
import io.github.heapy.icu4k.impl.data.CollationPaGuruResourceData
import io.github.heapy.icu4k.impl.data.CollationPaGuruINResourceData
import io.github.heapy.icu4k.impl.data.CollationPaINResourceData
import io.github.heapy.icu4k.impl.data.CollationPlResourceData
import io.github.heapy.icu4k.impl.data.CollationPsResourceData
import io.github.heapy.icu4k.impl.data.CollationPtResourceData
import io.github.heapy.icu4k.impl.data.CollationResIndexResourceData
import io.github.heapy.icu4k.impl.data.CollationRoResourceData
import io.github.heapy.icu4k.impl.data.CollationRootResourceData
import io.github.heapy.icu4k.impl.data.CollationRuResourceData
import io.github.heapy.icu4k.impl.data.CollationSaResourceData
import io.github.heapy.icu4k.impl.data.CollationSeResourceData
import io.github.heapy.icu4k.impl.data.CollationShResourceData
import io.github.heapy.icu4k.impl.data.CollationShBAResourceData
import io.github.heapy.icu4k.impl.data.CollationShCSResourceData
import io.github.heapy.icu4k.impl.data.CollationShYUResourceData
import io.github.heapy.icu4k.impl.data.CollationSiResourceData
import io.github.heapy.icu4k.impl.data.CollationSkResourceData
import io.github.heapy.icu4k.impl.data.CollationSlResourceData
import io.github.heapy.icu4k.impl.data.CollationSmnResourceData
import io.github.heapy.icu4k.impl.data.CollationSqResourceData
import io.github.heapy.icu4k.impl.data.CollationSrResourceData
import io.github.heapy.icu4k.impl.data.CollationSrBAResourceData
import io.github.heapy.icu4k.impl.data.CollationSrCyrlResourceData
import io.github.heapy.icu4k.impl.data.CollationSrCyrlBAResourceData
import io.github.heapy.icu4k.impl.data.CollationSrCyrlMEResourceData
import io.github.heapy.icu4k.impl.data.CollationSrCyrlRSResourceData
import io.github.heapy.icu4k.impl.data.CollationSrLatnResourceData
import io.github.heapy.icu4k.impl.data.CollationSrLatnBAResourceData
import io.github.heapy.icu4k.impl.data.CollationSrLatnRSResourceData
import io.github.heapy.icu4k.impl.data.CollationSrMEResourceData
import io.github.heapy.icu4k.impl.data.CollationSrRSResourceData
import io.github.heapy.icu4k.impl.data.CollationStResourceData
import io.github.heapy.icu4k.impl.data.CollationSvResourceData
import io.github.heapy.icu4k.impl.data.CollationSwResourceData
import io.github.heapy.icu4k.impl.data.CollationTaResourceData
import io.github.heapy.icu4k.impl.data.CollationTeResourceData
import io.github.heapy.icu4k.impl.data.CollationThResourceData
import io.github.heapy.icu4k.impl.data.CollationTkResourceData
import io.github.heapy.icu4k.impl.data.CollationTnResourceData
import io.github.heapy.icu4k.impl.data.CollationToResourceData
import io.github.heapy.icu4k.impl.data.CollationTrResourceData
import io.github.heapy.icu4k.impl.data.CollationUgResourceData
import io.github.heapy.icu4k.impl.data.CollationUkResourceData
import io.github.heapy.icu4k.impl.data.CollationUrResourceData
import io.github.heapy.icu4k.impl.data.CollationUzResourceData
import io.github.heapy.icu4k.impl.data.CollationViResourceData
import io.github.heapy.icu4k.impl.data.CollationWaeResourceData
import io.github.heapy.icu4k.impl.data.CollationWoResourceData
import io.github.heapy.icu4k.impl.data.CollationXhResourceData
import io.github.heapy.icu4k.impl.data.CollationYiResourceData
import io.github.heapy.icu4k.impl.data.CollationYoResourceData
import io.github.heapy.icu4k.impl.data.CollationYueResourceData
import io.github.heapy.icu4k.impl.data.CollationYueCNResourceData
import io.github.heapy.icu4k.impl.data.CollationYueHansResourceData
import io.github.heapy.icu4k.impl.data.CollationYueHansCNResourceData
import io.github.heapy.icu4k.impl.data.CollationYueHantResourceData
import io.github.heapy.icu4k.impl.data.CollationZhResourceData
import io.github.heapy.icu4k.impl.data.CollationZhCNResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHKResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHansResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHansCNResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHansSGResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHantResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHantHKResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHantMOResourceData
import io.github.heapy.icu4k.impl.data.CollationZhHantTWResourceData
import io.github.heapy.icu4k.impl.data.CollationZhMOResourceData
import io.github.heapy.icu4k.impl.data.CollationZhSGResourceData
import io.github.heapy.icu4k.impl.data.CollationZhTWResourceData
import io.github.heapy.icu4k.impl.data.CollationZuResourceData

import io.github.heapy.icu4k.impl.data.RbnfAfResourceData
import io.github.heapy.icu4k.impl.data.RbnfAkResourceData
import io.github.heapy.icu4k.impl.data.RbnfAmResourceData
import io.github.heapy.icu4k.impl.data.RbnfArResourceData
import io.github.heapy.icu4k.impl.data.RbnfArSAResourceData
import io.github.heapy.icu4k.impl.data.RbnfArsResourceData
import io.github.heapy.icu4k.impl.data.RbnfAzResourceData
import io.github.heapy.icu4k.impl.data.RbnfBeResourceData
import io.github.heapy.icu4k.impl.data.RbnfBgResourceData
import io.github.heapy.icu4k.impl.data.RbnfBsResourceData
import io.github.heapy.icu4k.impl.data.RbnfCaResourceData
import io.github.heapy.icu4k.impl.data.RbnfCcpResourceData
import io.github.heapy.icu4k.impl.data.RbnfChrResourceData
import io.github.heapy.icu4k.impl.data.RbnfCsResourceData
import io.github.heapy.icu4k.impl.data.RbnfCyResourceData
import io.github.heapy.icu4k.impl.data.RbnfDaResourceData
import io.github.heapy.icu4k.impl.data.RbnfDeResourceData
import io.github.heapy.icu4k.impl.data.RbnfDeCHResourceData
import io.github.heapy.icu4k.impl.data.RbnfEeResourceData
import io.github.heapy.icu4k.impl.data.RbnfElResourceData
import io.github.heapy.icu4k.impl.data.RbnfEnResourceData
import io.github.heapy.icu4k.impl.data.RbnfEn001ResourceData
import io.github.heapy.icu4k.impl.data.RbnfEnINResourceData
import io.github.heapy.icu4k.impl.data.RbnfEoResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsResourceData
import io.github.heapy.icu4k.impl.data.RbnfEs419ResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsDOResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsGTResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsHNResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsMXResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsNIResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsPAResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsPRResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsSVResourceData
import io.github.heapy.icu4k.impl.data.RbnfEsUSResourceData
import io.github.heapy.icu4k.impl.data.RbnfEtResourceData
import io.github.heapy.icu4k.impl.data.RbnfFaResourceData
import io.github.heapy.icu4k.impl.data.RbnfFaAFResourceData
import io.github.heapy.icu4k.impl.data.RbnfFfResourceData
import io.github.heapy.icu4k.impl.data.RbnfFiResourceData
import io.github.heapy.icu4k.impl.data.RbnfFilResourceData
import io.github.heapy.icu4k.impl.data.RbnfFoResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrBEResourceData
import io.github.heapy.icu4k.impl.data.RbnfFrCHResourceData
import io.github.heapy.icu4k.impl.data.RbnfGaResourceData
import io.github.heapy.icu4k.impl.data.RbnfGuResourceData
import io.github.heapy.icu4k.impl.data.RbnfHeResourceData
import io.github.heapy.icu4k.impl.data.RbnfHiResourceData
import io.github.heapy.icu4k.impl.data.RbnfHrResourceData
import io.github.heapy.icu4k.impl.data.RbnfHuResourceData
import io.github.heapy.icu4k.impl.data.RbnfHyResourceData
import io.github.heapy.icu4k.impl.data.RbnfIdResourceData
import io.github.heapy.icu4k.impl.data.RbnfInResourceData
import io.github.heapy.icu4k.impl.data.RbnfIsResourceData
import io.github.heapy.icu4k.impl.data.RbnfItResourceData
import io.github.heapy.icu4k.impl.data.RbnfIwResourceData
import io.github.heapy.icu4k.impl.data.RbnfJaResourceData
import io.github.heapy.icu4k.impl.data.RbnfKaResourceData
import io.github.heapy.icu4k.impl.data.RbnfKkResourceData
import io.github.heapy.icu4k.impl.data.RbnfKlResourceData
import io.github.heapy.icu4k.impl.data.RbnfKmResourceData
import io.github.heapy.icu4k.impl.data.RbnfKoResourceData
import io.github.heapy.icu4k.impl.data.RbnfKyResourceData
import io.github.heapy.icu4k.impl.data.RbnfLbResourceData
import io.github.heapy.icu4k.impl.data.RbnfLoResourceData
import io.github.heapy.icu4k.impl.data.RbnfLrcResourceData
import io.github.heapy.icu4k.impl.data.RbnfLtResourceData
import io.github.heapy.icu4k.impl.data.RbnfLvResourceData
import io.github.heapy.icu4k.impl.data.RbnfMkResourceData
import io.github.heapy.icu4k.impl.data.RbnfMsResourceData
import io.github.heapy.icu4k.impl.data.RbnfMtResourceData
import io.github.heapy.icu4k.impl.data.RbnfMyResourceData
import io.github.heapy.icu4k.impl.data.RbnfNbResourceData
import io.github.heapy.icu4k.impl.data.RbnfNeResourceData
import io.github.heapy.icu4k.impl.data.RbnfNlResourceData
import io.github.heapy.icu4k.impl.data.RbnfNnResourceData
import io.github.heapy.icu4k.impl.data.RbnfNoResourceData
import io.github.heapy.icu4k.impl.data.RbnfPlResourceData
import io.github.heapy.icu4k.impl.data.RbnfPtResourceData
import io.github.heapy.icu4k.impl.data.RbnfPtPTResourceData
import io.github.heapy.icu4k.impl.data.RbnfQuResourceData
import io.github.heapy.icu4k.impl.data.RbnfResIndexResourceData
import io.github.heapy.icu4k.impl.data.RbnfRoResourceData
import io.github.heapy.icu4k.impl.data.RbnfRootResourceData
import io.github.heapy.icu4k.impl.data.RbnfRuResourceData
import io.github.heapy.icu4k.impl.data.RbnfSeResourceData
import io.github.heapy.icu4k.impl.data.RbnfShResourceData
import io.github.heapy.icu4k.impl.data.RbnfSkResourceData
import io.github.heapy.icu4k.impl.data.RbnfSlResourceData
import io.github.heapy.icu4k.impl.data.RbnfSqResourceData
import io.github.heapy.icu4k.impl.data.RbnfSrResourceData
import io.github.heapy.icu4k.impl.data.RbnfSrLatnResourceData
import io.github.heapy.icu4k.impl.data.RbnfSuResourceData
import io.github.heapy.icu4k.impl.data.RbnfSvResourceData
import io.github.heapy.icu4k.impl.data.RbnfSwResourceData
import io.github.heapy.icu4k.impl.data.RbnfTaResourceData
import io.github.heapy.icu4k.impl.data.RbnfThResourceData
import io.github.heapy.icu4k.impl.data.RbnfTrResourceData
import io.github.heapy.icu4k.impl.data.RbnfUkResourceData
import io.github.heapy.icu4k.impl.data.RbnfVecResourceData
import io.github.heapy.icu4k.impl.data.RbnfViResourceData
import io.github.heapy.icu4k.impl.data.RbnfYueResourceData
import io.github.heapy.icu4k.impl.data.RbnfYueHansResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHKResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantHKResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantMOResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhHantTWResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhMOResourceData
import io.github.heapy.icu4k.impl.data.RbnfZhTWResourceData
import io.github.heapy.icu4k.impl.data.CurrencyNumericCodesResourceData
import io.github.heapy.icu4k.impl.data.CurrencyDeResourceData
import io.github.heapy.icu4k.impl.data.CurrencyZhResourceData
import io.github.heapy.icu4k.impl.data.CurrencyRuResourceData
import io.github.heapy.icu4k.impl.data.CurrencyHiResourceData
import io.github.heapy.icu4k.impl.data.CurrencyItResourceData
import io.github.heapy.icu4k.impl.data.CurrencyPtResourceData
import io.github.heapy.icu4k.impl.data.CurrencyKoResourceData
import io.github.heapy.icu4k.impl.data.CurrencyNlResourceData
import io.github.heapy.icu4k.impl.data.RegionRootResourceData
import io.github.heapy.icu4k.impl.data.RegionPoolResourceData
import io.github.heapy.icu4k.impl.data.RegionEnResourceData
import io.github.heapy.icu4k.impl.data.BreakEnResourceData
import io.github.heapy.icu4k.impl.data.BreakFrResourceData
import io.github.heapy.icu4k.impl.data.BreakDeResourceData
import io.github.heapy.icu4k.impl.data.BreakEsResourceData
import io.github.heapy.icu4k.impl.data.BreakElResourceData
import io.github.heapy.icu4k.impl.data.BreakKoResourceData
import io.github.heapy.icu4k.impl.data.BreakZhResourceData
import io.github.heapy.icu4k.impl.data.BreakZhHantResourceData
import io.github.heapy.icu4k.impl.data.BreakEnUsResourceData
import io.github.heapy.icu4k.impl.data.BreakEnUsPosixResourceData
import io.github.heapy.icu4k.impl.data.RegionFrResourceData
import io.github.heapy.icu4k.impl.data.RegionArResourceData
import io.github.heapy.icu4k.impl.data.RegionJaResourceData
import io.github.heapy.icu4k.impl.data.PluralsResourceData
import io.github.heapy.icu4k.impl.data.PluralRangesResourceData
import io.github.heapy.icu4k.impl.data.UnitsResourceData
import io.github.heapy.icu4k.impl.data.CurrencyRootResourceData
import io.github.heapy.icu4k.impl.data.CurrencyPoolResourceData
import io.github.heapy.icu4k.impl.data.CurrencyEnResourceData
import io.github.heapy.icu4k.impl.data.CurrencyFrResourceData
import io.github.heapy.icu4k.impl.data.CurrencyArResourceData
import io.github.heapy.icu4k.impl.data.CurrencyJaResourceData
import io.github.heapy.icu4k.impl.data.CurrencyEsResourceData
import io.github.heapy.icu4k.impl.data.CurrencyHeResourceData
import io.github.heapy.icu4k.impl.data.CurrencySupplementalResourceData
import io.github.heapy.icu4k.impl.data.NumberingSystemsResourceData
import io.github.heapy.icu4k.impl.data.CoreEsResourceData
import io.github.heapy.icu4k.impl.data.CoreHeResourceData
import io.github.heapy.icu4k.impl.data.CoreEnResourceData
import io.github.heapy.icu4k.impl.data.CoreFrResourceData
import io.github.heapy.icu4k.impl.data.CoreArResourceData
import io.github.heapy.icu4k.impl.data.CoreJaResourceData
import io.github.heapy.icu4k.impl.data.LanguageRootResourceData
import io.github.heapy.icu4k.impl.data.LanguagePoolResourceData
import io.github.heapy.icu4k.impl.data.LanguageEnResourceData
import io.github.heapy.icu4k.impl.data.LanguageFrResourceData
import io.github.heapy.icu4k.impl.data.LanguageArResourceData
import io.github.heapy.icu4k.impl.data.LanguageJaResourceData
import io.github.heapy.icu4k.impl.data.SupplementalResourceData
import io.github.heapy.icu4k.impl.data.LanguageInfoResourceData
import io.github.heapy.icu4k.impl.data.GrammaticalFeaturesResourceData
import io.github.heapy.icu4k.impl.data.GermanResourceData
import io.github.heapy.icu4k.impl.data.RussianResourceData
import io.github.heapy.icu4k.impl.data.LocaleMetadataResourceData
import io.github.heapy.icu4k.impl.data.RootResourceData
import io.github.heapy.icu4k.impl.data.PoolResourceData
import io.github.heapy.icu4k.impl.data.RootLocaleIndexData
import io.github.heapy.icu4k.impl.data.BreakLocaleIndexData
import io.github.heapy.icu4k.impl.data.TimezoneTypeResourceData
import io.github.heapy.icu4k.impl.data.KeyTypeResourceData
import io.github.heapy.icu4k.impl.data.BurmeseLstmModelData

import io.github.heapy.icu4k.impl.data.ThaiLstmModelData

import io.github.heapy.icu4k.impl.data.BreakJapaneseMlResourceData

import io.github.heapy.icu4k.impl.data.BreakJapaneseResourceData
import io.github.heapy.icu4k.impl.data.BreakRootResourceData

import io.github.heapy.icu4k.impl.data.UnamesData
import io.github.heapy.icu4k.impl.data.Uts46Data

import io.github.heapy.icu4k.impl.data.NfkcScfData

import io.github.heapy.icu4k.impl.data.NfkcCfData

import io.github.heapy.icu4k.impl.data.NfkcData

import io.github.heapy.icu4k.impl.data.NfcData

import io.github.heapy.icu4k.impl.data.BreakCharData
import io.github.heapy.icu4k.impl.data.BreakLineData
import io.github.heapy.icu4k.impl.data.BreakLineCjData
import io.github.heapy.icu4k.impl.data.BreakLineLooseData
import io.github.heapy.icu4k.impl.data.BreakLineLooseCjData
import io.github.heapy.icu4k.impl.data.BreakLineLoosePhraseCjData
import io.github.heapy.icu4k.impl.data.BreakLineNormalData
import io.github.heapy.icu4k.impl.data.BreakLineNormalCjData
import io.github.heapy.icu4k.impl.data.BreakLineNormalPhraseCjData
import io.github.heapy.icu4k.impl.data.BreakLinePhraseCjData
import io.github.heapy.icu4k.impl.data.BreakSentData
import io.github.heapy.icu4k.impl.data.BreakSentElData
import io.github.heapy.icu4k.impl.data.BreakTitleData
import io.github.heapy.icu4k.impl.data.BreakWordData
import io.github.heapy.icu4k.impl.data.BreakWordPosixData
import io.github.heapy.icu4k.impl.data.DictionaryBurmesedictData
import io.github.heapy.icu4k.impl.data.DictionaryCjdictData
import io.github.heapy.icu4k.impl.data.DictionaryKhmerdictData
import io.github.heapy.icu4k.impl.data.DictionaryLaodictData
import io.github.heapy.icu4k.impl.data.DictionaryThaidictData
import io.github.heapy.icu4k.impl.data.UcaseData
import io.github.heapy.icu4k.impl.data.UbidiData
import io.github.heapy.icu4k.impl.data.UlayoutData
import io.github.heapy.icu4k.impl.data.UpropsData
import io.github.heapy.icu4k.impl.data.PnamesData
import io.github.heapy.icu4k.impl.data.UemojiData

/** Version-pinned ICU resources available identically on JVM, JS and Native. */
object ICUData {
    internal const val PACKAGE_NAME = "icudt78b"
    const val ICU_BUNDLE = "data/icudata"
    const val ICU_BASE_NAME = "com/ibm/icu/impl/$ICU_BUNDLE"
    const val ICU_COLLATION_BASE_NAME = "$ICU_BASE_NAME/coll"
    const val ICU_BRKITR_NAME = "brkitr"
    const val ICU_BRKITR_BASE_NAME = "$ICU_BASE_NAME/$ICU_BRKITR_NAME"
    const val ICU_RBNF_BASE_NAME = "$ICU_BASE_NAME/rbnf"
    const val ICU_TRANSLIT_BASE_NAME = "$ICU_BASE_NAME/translit"
    const val ICU_LANG_BASE_NAME = "$ICU_BASE_NAME/lang"
    const val ICU_CURR_BASE_NAME = "$ICU_BASE_NAME/curr"
    const val ICU_REGION_BASE_NAME = "$ICU_BASE_NAME/region"
    const val ICU_ZONE_BASE_NAME = "$ICU_BASE_NAME/zone"
    const val ICU_UNIT_BASE_NAME = "$ICU_BASE_NAME/unit"

    private val resourceNames = setOf("windowsZones.res", "zoneinfo64.res", "metaZones.res", "coll/af.res", "coll/am.res", "coll/ar.res", "coll/ar_SA.res", "coll/ars.res", "coll/as.res", "coll/az.res", "coll/be.res", "coll/bg.res", "coll/blo.res", "coll/bn.res", "coll/bo.res", "coll/br.res", "coll/bs.res", "coll/bs_Cyrl.res", "coll/ca.res", "coll/ceb.res", "coll/chr.res", "coll/cs.res", "coll/cy.res", "coll/da.res", "coll/de.res", "coll/de_.res", "coll/de_AT.res", "coll/de__PHONEBOOK.res", "coll/dsb.res", "coll/dz.res", "coll/ee.res", "coll/el.res", "coll/en.res", "coll/en_US.res", "coll/en_US_POSIX.res", "coll/eo.res", "coll/es.res", "coll/es_.res", "coll/es__TRADITIONAL.res", "coll/et.res", "coll/fa.res", "coll/fa_AF.res", "coll/ff.res", "coll/ff_Adlm.res", "coll/fi.res", "coll/fil.res", "coll/fo.res", "coll/fr.res", "coll/fr_CA.res", "coll/fy.res", "coll/ga.res", "coll/gl.res", "coll/gu.res", "coll/ha.res", "coll/haw.res", "coll/he.res", "coll/he_IL.res", "coll/hi.res", "coll/hr.res", "coll/hsb.res", "coll/hu.res", "coll/hy.res", "coll/id.res", "coll/id_ID.res", "coll/ig.res", "coll/in.res", "coll/in_ID.res", "coll/is.res", "coll/it.res", "coll/iw.res", "coll/iw_IL.res", "coll/ja.res", "coll/ka.res", "coll/kk.res", "coll/kk_Arab.res", "coll/kl.res", "coll/km.res", "coll/kn.res", "coll/ko.res", "coll/kok.res", "coll/ku.res", "coll/ky.res", "coll/lb.res", "coll/lij.res", "coll/lkt.res", "coll/ln.res", "coll/lo.res", "coll/lt.res", "coll/lv.res", "coll/mk.res", "coll/ml.res", "coll/mn.res", "coll/mo.res", "coll/mr.res", "coll/ms.res", "coll/mt.res", "coll/my.res", "coll/nb.res", "coll/nb_NO.res", "coll/ne.res", "coll/nl.res", "coll/nn.res", "coll/no.res", "coll/no_NO.res", "coll/nso.res", "coll/om.res", "coll/or.res", "coll/pa.res", "coll/pa_Guru.res", "coll/pa_Guru_IN.res", "coll/pa_IN.res", "coll/pl.res", "coll/ps.res", "coll/pt.res", "coll/res_index.res", "coll/ro.res", "coll/root.res", "coll/ru.res", "coll/sa.res", "coll/se.res", "coll/sh.res", "coll/sh_BA.res", "coll/sh_CS.res", "coll/sh_YU.res", "coll/si.res", "coll/sk.res", "coll/sl.res", "coll/smn.res", "coll/sq.res", "coll/sr.res", "coll/sr_BA.res", "coll/sr_Cyrl.res", "coll/sr_Cyrl_BA.res", "coll/sr_Cyrl_ME.res", "coll/sr_Cyrl_RS.res", "coll/sr_Latn.res", "coll/sr_Latn_BA.res", "coll/sr_Latn_RS.res", "coll/sr_ME.res", "coll/sr_RS.res", "coll/st.res", "coll/sv.res", "coll/sw.res", "coll/ta.res", "coll/te.res", "coll/th.res", "coll/tk.res", "coll/tn.res", "coll/to.res", "coll/tr.res", "coll/ug.res", "coll/uk.res", "coll/ur.res", "coll/uz.res", "coll/vi.res", "coll/wae.res", "coll/wo.res", "coll/xh.res", "coll/yi.res", "coll/yo.res", "coll/yue.res", "coll/yue_CN.res", "coll/yue_Hans.res", "coll/yue_Hans_CN.res", "coll/yue_Hant.res", "coll/zh.res", "coll/zh_CN.res", "coll/zh_HK.res", "coll/zh_Hans.res", "coll/zh_Hans_CN.res", "coll/zh_Hans_SG.res", "coll/zh_Hant.res", "coll/zh_Hant_HK.res", "coll/zh_Hant_MO.res", "coll/zh_Hant_TW.res", "coll/zh_MO.res", "coll/zh_SG.res", "coll/zh_TW.res", "coll/zu.res", "coll/ucadata.icu", "plurals.res", "pluralRanges.res", "ru.res", "de.res", "grammaticalFeatures.res", "region/fr.res", "region/ar.res", "region/ja.res", "brkitr/en_US.res", "brkitr/en_US_POSIX.res", "brkitr/el.res", "brkitr/ko.res", "brkitr/zh.res", "brkitr/zh_Hant.res", "brkitr/en.res", "brkitr/fr.res", "brkitr/de.res", "brkitr/es.res", "region/root.res", "region/pool.res", "region/en.res", "curr/de.res", "curr/zh.res", "curr/ru.res", "curr/hi.res", "curr/it.res", "curr/pt.res", "curr/ko.res", "curr/nl.res", "currencyNumericCodes.res", "units.res", "curr/he.res", "curr/es.res", "curr/ja.res", "curr/ar.res", "curr/fr.res", "curr/en.res", "curr/pool.res", "curr/root.res", "curr/supplementalData.res", "numberingSystems.res", "es.res", "he.res", "lang/ja.res", "lang/ar.res", "lang/fr.res", "lang/en.res", "lang/pool.res", "lang/root.res", "ja.res", "ar.res", "fr.res", "en.res", "supplementalData.res", "langInfo.res", "metadata.res", "root.res", "pool.res", "res_index.res", "brkitr/res_index.res", "timezoneTypes.res", "keyTypeData.res", "brkitr/Burmese_graphclust_model5_heavy.res", "brkitr/Thai_graphclust_model4_heavy.res", "brkitr/jaml.res", "brkitr/ja.res", "brkitr/root.res", "unames.icu", "uts46.nrm", "nfkc_scf.nrm", "nfkc_cf.nrm", "nfkc.nrm", "nfc.nrm", "uemoji.icu", "pnames.icu", "ubidi.icu", "ucase.icu", "ulayout.icu", "uprops.icu", "brkitr/char.brk", "brkitr/line.brk", "brkitr/line_cj.brk", "brkitr/line_loose.brk", "brkitr/line_loose_cj.brk", "brkitr/line_loose_phrase_cj.brk", "brkitr/line_normal.brk", "brkitr/line_normal_cj.brk", "brkitr/line_normal_phrase_cj.brk", "brkitr/line_phrase_cj.brk", "brkitr/sent.brk", "brkitr/sent_el.brk", "brkitr/title.brk", "brkitr/word.brk", "brkitr/word_POSIX.brk", "brkitr/burmesedict.dict", "brkitr/cjdict.dict", "brkitr/khmerdict.dict", "brkitr/laodict.dict", "brkitr/thaidict.dict",
        "rbnf/af.res",
        "rbnf/ak.res",
        "rbnf/am.res",
        "rbnf/ar.res",
        "rbnf/ar_SA.res",
        "rbnf/ars.res",
        "rbnf/az.res",
        "rbnf/be.res",
        "rbnf/bg.res",
        "rbnf/bs.res",
        "rbnf/ca.res",
        "rbnf/ccp.res",
        "rbnf/chr.res",
        "rbnf/cs.res",
        "rbnf/cy.res",
        "rbnf/da.res",
        "rbnf/de.res",
        "rbnf/de_CH.res",
        "rbnf/ee.res",
        "rbnf/el.res",
        "rbnf/en.res",
        "rbnf/en_001.res",
        "rbnf/en_IN.res",
        "rbnf/eo.res",
        "rbnf/es.res",
        "rbnf/es_419.res",
        "rbnf/es_DO.res",
        "rbnf/es_GT.res",
        "rbnf/es_HN.res",
        "rbnf/es_MX.res",
        "rbnf/es_NI.res",
        "rbnf/es_PA.res",
        "rbnf/es_PR.res",
        "rbnf/es_SV.res",
        "rbnf/es_US.res",
        "rbnf/et.res",
        "rbnf/fa.res",
        "rbnf/fa_AF.res",
        "rbnf/ff.res",
        "rbnf/fi.res",
        "rbnf/fil.res",
        "rbnf/fo.res",
        "rbnf/fr.res",
        "rbnf/fr_BE.res",
        "rbnf/fr_CH.res",
        "rbnf/ga.res",
        "rbnf/gu.res",
        "rbnf/he.res",
        "rbnf/hi.res",
        "rbnf/hr.res",
        "rbnf/hu.res",
        "rbnf/hy.res",
        "rbnf/id.res",
        "rbnf/in.res",
        "rbnf/is.res",
        "rbnf/it.res",
        "rbnf/iw.res",
        "rbnf/ja.res",
        "rbnf/ka.res",
        "rbnf/kk.res",
        "rbnf/kl.res",
        "rbnf/km.res",
        "rbnf/ko.res",
        "rbnf/ky.res",
        "rbnf/lb.res",
        "rbnf/lo.res",
        "rbnf/lrc.res",
        "rbnf/lt.res",
        "rbnf/lv.res",
        "rbnf/mk.res",
        "rbnf/ms.res",
        "rbnf/mt.res",
        "rbnf/my.res",
        "rbnf/nb.res",
        "rbnf/ne.res",
        "rbnf/nl.res",
        "rbnf/nn.res",
        "rbnf/no.res",
        "rbnf/pl.res",
        "rbnf/pt.res",
        "rbnf/pt_PT.res",
        "rbnf/qu.res",
        "rbnf/res_index.res",
        "rbnf/ro.res",
        "rbnf/root.res",
        "rbnf/ru.res",
        "rbnf/se.res",
        "rbnf/sh.res",
        "rbnf/sk.res",
        "rbnf/sl.res",
        "rbnf/sq.res",
        "rbnf/sr.res",
        "rbnf/sr_Latn.res",
        "rbnf/su.res",
        "rbnf/sv.res",
        "rbnf/sw.res",
        "rbnf/ta.res",
        "rbnf/th.res",
        "rbnf/tr.res",
        "rbnf/uk.res",
        "rbnf/vec.res",
        "rbnf/vi.res",
        "rbnf/yue.res",
        "rbnf/yue_Hans.res",
        "rbnf/zh.res",
        "rbnf/zh_HK.res",
        "rbnf/zh_Hant.res",
        "rbnf/zh_Hant_HK.res",
        "rbnf/zh_Hant_MO.res",
        "rbnf/zh_Hant_TW.res",
        "rbnf/zh_MO.res",
        "rbnf/zh_TW.res",
    ) + ZoneResourceRegistry.names + RegionResourceRegistry.names + CoreResourceRegistry.names + UnitResourceRegistry.names
    internal fun visitResources(directory: String, visitor: (String) -> Unit) {
        val prefix = directory.removePrefix("/")
        if (!prefix.startsWith("$ICU_BASE_NAME/")) return
        val folder = prefix.removePrefix("$ICU_BASE_NAME/")
        for (name in resourceNames) if (name.startsWith(folder)) {
            val relative = name.substring(folder.length)
            if ('/' !in relative) visitor(relative)
        }
    }
    private fun itemPath(name: String): String = name.removePrefix("/").removePrefix("$ICU_BASE_NAME/")
    fun exists(resourceName: String): Boolean = itemPath(resourceName) in resourceNames

    /** Returns a private copy, so callers cannot mutate cached resource data. */
    fun getBytes(resourceName: String): ByteArray? = when (itemPath(resourceName)) {
        "coll/af.res" -> CollationAfResourceData.copy()
        "coll/am.res" -> CollationAmResourceData.copy()
        "coll/ar.res" -> CollationArResourceData.copy()
        "coll/ar_SA.res" -> CollationArSAResourceData.copy()
        "coll/ars.res" -> CollationArsResourceData.copy()
        "coll/as.res" -> CollationAsResourceData.copy()
        "coll/az.res" -> CollationAzResourceData.copy()
        "coll/be.res" -> CollationBeResourceData.copy()
        "coll/bg.res" -> CollationBgResourceData.copy()
        "coll/blo.res" -> CollationBloResourceData.copy()
        "coll/bn.res" -> CollationBnResourceData.copy()
        "coll/bo.res" -> CollationBoResourceData.copy()
        "coll/br.res" -> CollationBrResourceData.copy()
        "coll/bs.res" -> CollationBsResourceData.copy()
        "coll/bs_Cyrl.res" -> CollationBsCyrlResourceData.copy()
        "coll/ca.res" -> CollationCaResourceData.copy()
        "coll/ceb.res" -> CollationCebResourceData.copy()
        "coll/chr.res" -> CollationChrResourceData.copy()
        "coll/cs.res" -> CollationCsResourceData.copy()
        "coll/cy.res" -> CollationCyResourceData.copy()
        "coll/da.res" -> CollationDaResourceData.copy()
        "coll/de.res" -> CollationDeResourceData.copy()
        "coll/de_.res" -> CollationDe_ResourceData.copy()
        "coll/de_AT.res" -> CollationDeATResourceData.copy()
        "coll/de__PHONEBOOK.res" -> CollationDe_PHONEBOOKResourceData.copy()
        "coll/dsb.res" -> CollationDsbResourceData.copy()
        "coll/dz.res" -> CollationDzResourceData.copy()
        "coll/ee.res" -> CollationEeResourceData.copy()
        "coll/el.res" -> CollationElResourceData.copy()
        "coll/en.res" -> CollationEnResourceData.copy()
        "coll/en_US.res" -> CollationEnUSResourceData.copy()
        "coll/en_US_POSIX.res" -> CollationEnUSPOSIXResourceData.copy()
        "coll/eo.res" -> CollationEoResourceData.copy()
        "coll/es.res" -> CollationEsResourceData.copy()
        "coll/es_.res" -> CollationEs_ResourceData.copy()
        "coll/es__TRADITIONAL.res" -> CollationEs_TRADITIONALResourceData.copy()
        "coll/et.res" -> CollationEtResourceData.copy()
        "coll/fa.res" -> CollationFaResourceData.copy()
        "coll/fa_AF.res" -> CollationFaAFResourceData.copy()
        "coll/ff.res" -> CollationFfResourceData.copy()
        "coll/ff_Adlm.res" -> CollationFfAdlmResourceData.copy()
        "coll/fi.res" -> CollationFiResourceData.copy()
        "coll/fil.res" -> CollationFilResourceData.copy()
        "coll/fo.res" -> CollationFoResourceData.copy()
        "coll/fr.res" -> CollationFrResourceData.copy()
        "coll/fr_CA.res" -> CollationFrCAResourceData.copy()
        "coll/fy.res" -> CollationFyResourceData.copy()
        "coll/ga.res" -> CollationGaResourceData.copy()
        "coll/gl.res" -> CollationGlResourceData.copy()
        "coll/gu.res" -> CollationGuResourceData.copy()
        "coll/ha.res" -> CollationHaResourceData.copy()
        "coll/haw.res" -> CollationHawResourceData.copy()
        "coll/he.res" -> CollationHeResourceData.copy()
        "coll/he_IL.res" -> CollationHeILResourceData.copy()
        "coll/hi.res" -> CollationHiResourceData.copy()
        "coll/hr.res" -> CollationHrResourceData.copy()
        "coll/hsb.res" -> CollationHsbResourceData.copy()
        "coll/hu.res" -> CollationHuResourceData.copy()
        "coll/hy.res" -> CollationHyResourceData.copy()
        "coll/id.res" -> CollationIdResourceData.copy()
        "coll/id_ID.res" -> CollationIdIDResourceData.copy()
        "coll/ig.res" -> CollationIgResourceData.copy()
        "coll/in.res" -> CollationInResourceData.copy()
        "coll/in_ID.res" -> CollationInIDResourceData.copy()
        "coll/is.res" -> CollationIsResourceData.copy()
        "coll/it.res" -> CollationItResourceData.copy()
        "coll/iw.res" -> CollationIwResourceData.copy()
        "coll/iw_IL.res" -> CollationIwILResourceData.copy()
        "coll/ja.res" -> CollationJaResourceData.copy()
        "coll/ka.res" -> CollationKaResourceData.copy()
        "coll/kk.res" -> CollationKkResourceData.copy()
        "coll/kk_Arab.res" -> CollationKkArabResourceData.copy()
        "coll/kl.res" -> CollationKlResourceData.copy()
        "coll/km.res" -> CollationKmResourceData.copy()
        "coll/kn.res" -> CollationKnResourceData.copy()
        "coll/ko.res" -> CollationKoResourceData.copy()
        "coll/kok.res" -> CollationKokResourceData.copy()
        "coll/ku.res" -> CollationKuResourceData.copy()
        "coll/ky.res" -> CollationKyResourceData.copy()
        "coll/lb.res" -> CollationLbResourceData.copy()
        "coll/lij.res" -> CollationLijResourceData.copy()
        "coll/lkt.res" -> CollationLktResourceData.copy()
        "coll/ln.res" -> CollationLnResourceData.copy()
        "coll/lo.res" -> CollationLoResourceData.copy()
        "coll/lt.res" -> CollationLtResourceData.copy()
        "coll/lv.res" -> CollationLvResourceData.copy()
        "coll/mk.res" -> CollationMkResourceData.copy()
        "coll/ml.res" -> CollationMlResourceData.copy()
        "coll/mn.res" -> CollationMnResourceData.copy()
        "coll/mo.res" -> CollationMoResourceData.copy()
        "coll/mr.res" -> CollationMrResourceData.copy()
        "coll/ms.res" -> CollationMsResourceData.copy()
        "coll/mt.res" -> CollationMtResourceData.copy()
        "coll/my.res" -> CollationMyResourceData.copy()
        "coll/nb.res" -> CollationNbResourceData.copy()
        "coll/nb_NO.res" -> CollationNbNOResourceData.copy()
        "coll/ne.res" -> CollationNeResourceData.copy()
        "coll/nl.res" -> CollationNlResourceData.copy()
        "coll/nn.res" -> CollationNnResourceData.copy()
        "coll/no.res" -> CollationNoResourceData.copy()
        "coll/no_NO.res" -> CollationNoNOResourceData.copy()
        "coll/nso.res" -> CollationNsoResourceData.copy()
        "coll/om.res" -> CollationOmResourceData.copy()
        "coll/or.res" -> CollationOrResourceData.copy()
        "coll/pa.res" -> CollationPaResourceData.copy()
        "coll/pa_Guru.res" -> CollationPaGuruResourceData.copy()
        "coll/pa_Guru_IN.res" -> CollationPaGuruINResourceData.copy()
        "coll/pa_IN.res" -> CollationPaINResourceData.copy()
        "coll/pl.res" -> CollationPlResourceData.copy()
        "coll/ps.res" -> CollationPsResourceData.copy()
        "coll/pt.res" -> CollationPtResourceData.copy()
        "coll/res_index.res" -> CollationResIndexResourceData.copy()
        "coll/ro.res" -> CollationRoResourceData.copy()
        "coll/root.res" -> CollationRootResourceData.copy()
        "coll/ru.res" -> CollationRuResourceData.copy()
        "coll/sa.res" -> CollationSaResourceData.copy()
        "coll/se.res" -> CollationSeResourceData.copy()
        "coll/sh.res" -> CollationShResourceData.copy()
        "coll/sh_BA.res" -> CollationShBAResourceData.copy()
        "coll/sh_CS.res" -> CollationShCSResourceData.copy()
        "coll/sh_YU.res" -> CollationShYUResourceData.copy()
        "coll/si.res" -> CollationSiResourceData.copy()
        "coll/sk.res" -> CollationSkResourceData.copy()
        "coll/sl.res" -> CollationSlResourceData.copy()
        "coll/smn.res" -> CollationSmnResourceData.copy()
        "coll/sq.res" -> CollationSqResourceData.copy()
        "coll/sr.res" -> CollationSrResourceData.copy()
        "coll/sr_BA.res" -> CollationSrBAResourceData.copy()
        "coll/sr_Cyrl.res" -> CollationSrCyrlResourceData.copy()
        "coll/sr_Cyrl_BA.res" -> CollationSrCyrlBAResourceData.copy()
        "coll/sr_Cyrl_ME.res" -> CollationSrCyrlMEResourceData.copy()
        "coll/sr_Cyrl_RS.res" -> CollationSrCyrlRSResourceData.copy()
        "coll/sr_Latn.res" -> CollationSrLatnResourceData.copy()
        "coll/sr_Latn_BA.res" -> CollationSrLatnBAResourceData.copy()
        "coll/sr_Latn_RS.res" -> CollationSrLatnRSResourceData.copy()
        "coll/sr_ME.res" -> CollationSrMEResourceData.copy()
        "coll/sr_RS.res" -> CollationSrRSResourceData.copy()
        "coll/st.res" -> CollationStResourceData.copy()
        "coll/sv.res" -> CollationSvResourceData.copy()
        "coll/sw.res" -> CollationSwResourceData.copy()
        "coll/ta.res" -> CollationTaResourceData.copy()
        "coll/te.res" -> CollationTeResourceData.copy()
        "coll/th.res" -> CollationThResourceData.copy()
        "coll/tk.res" -> CollationTkResourceData.copy()
        "coll/tn.res" -> CollationTnResourceData.copy()
        "coll/to.res" -> CollationToResourceData.copy()
        "coll/tr.res" -> CollationTrResourceData.copy()
        "coll/ug.res" -> CollationUgResourceData.copy()
        "coll/uk.res" -> CollationUkResourceData.copy()
        "coll/ur.res" -> CollationUrResourceData.copy()
        "coll/uz.res" -> CollationUzResourceData.copy()
        "coll/vi.res" -> CollationViResourceData.copy()
        "coll/wae.res" -> CollationWaeResourceData.copy()
        "coll/wo.res" -> CollationWoResourceData.copy()
        "coll/xh.res" -> CollationXhResourceData.copy()
        "coll/yi.res" -> CollationYiResourceData.copy()
        "coll/yo.res" -> CollationYoResourceData.copy()
        "coll/yue.res" -> CollationYueResourceData.copy()
        "coll/yue_CN.res" -> CollationYueCNResourceData.copy()
        "coll/yue_Hans.res" -> CollationYueHansResourceData.copy()
        "coll/yue_Hans_CN.res" -> CollationYueHansCNResourceData.copy()
        "coll/yue_Hant.res" -> CollationYueHantResourceData.copy()
        "coll/zh.res" -> CollationZhResourceData.copy()
        "coll/zh_CN.res" -> CollationZhCNResourceData.copy()
        "coll/zh_HK.res" -> CollationZhHKResourceData.copy()
        "coll/zh_Hans.res" -> CollationZhHansResourceData.copy()
        "coll/zh_Hans_CN.res" -> CollationZhHansCNResourceData.copy()
        "coll/zh_Hans_SG.res" -> CollationZhHansSGResourceData.copy()
        "coll/zh_Hant.res" -> CollationZhHantResourceData.copy()
        "coll/zh_Hant_HK.res" -> CollationZhHantHKResourceData.copy()
        "coll/zh_Hant_MO.res" -> CollationZhHantMOResourceData.copy()
        "coll/zh_Hant_TW.res" -> CollationZhHantTWResourceData.copy()
        "coll/zh_MO.res" -> CollationZhMOResourceData.copy()
        "coll/zh_SG.res" -> CollationZhSGResourceData.copy()
        "coll/zh_TW.res" -> CollationZhTWResourceData.copy()
        "coll/zu.res" -> CollationZuResourceData.copy()
        "coll/ucadata.icu" -> UcaData.copy()
        "currencyNumericCodes.res" -> CurrencyNumericCodesResourceData.copy()
        "curr/de.res" -> CurrencyDeResourceData.copy()
        "curr/zh.res" -> CurrencyZhResourceData.copy()
        "curr/ru.res" -> CurrencyRuResourceData.copy()
        "curr/hi.res" -> CurrencyHiResourceData.copy()
        "curr/it.res" -> CurrencyItResourceData.copy()
        "curr/pt.res" -> CurrencyPtResourceData.copy()
        "curr/ko.res" -> CurrencyKoResourceData.copy()
        "curr/nl.res" -> CurrencyNlResourceData.copy()
        "region/root.res" -> RegionRootResourceData.copy()
        "region/pool.res" -> RegionPoolResourceData.copy()
        "region/en.res" -> RegionEnResourceData.copy()
        "brkitr/en.res" -> BreakEnResourceData.copy()
        "brkitr/fr.res" -> BreakFrResourceData.copy()
        "brkitr/de.res" -> BreakDeResourceData.copy()
        "brkitr/es.res" -> BreakEsResourceData.copy()
        "brkitr/el.res" -> BreakElResourceData.copy()
        "brkitr/ko.res" -> BreakKoResourceData.copy()
        "brkitr/zh.res" -> BreakZhResourceData.copy()
        "brkitr/zh_Hant.res" -> BreakZhHantResourceData.copy()
        "brkitr/en_US.res" -> BreakEnUsResourceData.copy()
        "brkitr/en_US_POSIX.res" -> BreakEnUsPosixResourceData.copy()
        "region/fr.res" -> RegionFrResourceData.copy()
        "region/ar.res" -> RegionArResourceData.copy()
        "region/ja.res" -> RegionJaResourceData.copy()
        "units.res" -> UnitsResourceData.copy()
        "plurals.res" -> PluralsResourceData.copy()
        "pluralRanges.res" -> PluralRangesResourceData.copy()
        "curr/root.res" -> CurrencyRootResourceData.copy()
        "curr/pool.res" -> CurrencyPoolResourceData.copy()
        "curr/en.res" -> CurrencyEnResourceData.copy()
        "curr/fr.res" -> CurrencyFrResourceData.copy()
        "curr/ar.res" -> CurrencyArResourceData.copy()
        "curr/ja.res" -> CurrencyJaResourceData.copy()
        "curr/es.res" -> CurrencyEsResourceData.copy()
        "curr/he.res" -> CurrencyHeResourceData.copy()
        "curr/supplementalData.res" -> CurrencySupplementalResourceData.copy()
        "numberingSystems.res" -> NumberingSystemsResourceData.copy()
        "es.res" -> CoreEsResourceData.copy()
        "he.res" -> CoreHeResourceData.copy()
        "en.res" -> CoreEnResourceData.copy()
        "fr.res" -> CoreFrResourceData.copy()
        "ar.res" -> CoreArResourceData.copy()
        "ja.res" -> CoreJaResourceData.copy()
        "lang/root.res" -> LanguageRootResourceData.copy()
        "lang/pool.res" -> LanguagePoolResourceData.copy()
        "lang/en.res" -> LanguageEnResourceData.copy()
        "lang/fr.res" -> LanguageFrResourceData.copy()
        "lang/ar.res" -> LanguageArResourceData.copy()
        "lang/ja.res" -> LanguageJaResourceData.copy()
        "supplementalData.res" -> SupplementalResourceData.copy()
        "langInfo.res" -> LanguageInfoResourceData.copy()
        "grammaticalFeatures.res" -> GrammaticalFeaturesResourceData.copy()
        "de.res" -> GermanResourceData.copy()
        "ru.res" -> RussianResourceData.copy()
        "metadata.res" -> LocaleMetadataResourceData.copy()
        "root.res" -> RootResourceData.copy()
        "pool.res" -> PoolResourceData.copy()
        "res_index.res" -> RootLocaleIndexData.copy()
        "brkitr/res_index.res" -> BreakLocaleIndexData.copy()
        "windowsZones.res" -> WindowsZonesResourceData.copy()
        "zoneinfo64.res" -> ZoneInfoResourceData.copy()
            "metaZones.res" -> MetaZonesResourceData.copy()
            "timezoneTypes.res" -> TimezoneTypeResourceData.copy()
        "keyTypeData.res" -> KeyTypeResourceData.copy()
        "brkitr/ja.res" -> BreakJapaneseResourceData.copy()
        "brkitr/root.res" -> BreakRootResourceData.copy()
        "brkitr/jaml.res" -> BreakJapaneseMlResourceData.copy()
        "brkitr/Thai_graphclust_model4_heavy.res" -> ThaiLstmModelData.copy()
        "brkitr/Burmese_graphclust_model5_heavy.res" -> BurmeseLstmModelData.copy()
        "unames.icu" -> UnamesData.copy()
        "nfc.nrm" -> NfcData.copy()
        "nfkc.nrm" -> NfkcData.copy()
        "nfkc_cf.nrm" -> NfkcCfData.copy()
        "nfkc_scf.nrm" -> NfkcScfData.copy()
        "uts46.nrm" -> Uts46Data.copy()
        "uemoji.icu" -> UemojiData.copy()
        "pnames.icu" -> PnamesData.copy()
        "ucase.icu" -> UcaseData.copy()
        "ubidi.icu" -> UbidiData.copy()
        "uprops.icu" -> UpropsData.copy()
        "ulayout.icu" -> UlayoutData.copy()
        "brkitr/char.brk" -> BreakCharData.copy()
        "brkitr/line.brk" -> BreakLineData.copy()
        "brkitr/line_cj.brk" -> BreakLineCjData.copy()
        "brkitr/line_loose.brk" -> BreakLineLooseData.copy()
        "brkitr/line_loose_cj.brk" -> BreakLineLooseCjData.copy()
        "brkitr/line_loose_phrase_cj.brk" -> BreakLineLoosePhraseCjData.copy()
        "brkitr/line_normal.brk" -> BreakLineNormalData.copy()
        "brkitr/line_normal_cj.brk" -> BreakLineNormalCjData.copy()
        "brkitr/line_normal_phrase_cj.brk" -> BreakLineNormalPhraseCjData.copy()
        "brkitr/line_phrase_cj.brk" -> BreakLinePhraseCjData.copy()
        "brkitr/sent.brk" -> BreakSentData.copy()
        "brkitr/sent_el.brk" -> BreakSentElData.copy()
        "brkitr/title.brk" -> BreakTitleData.copy()
        "brkitr/word.brk" -> BreakWordData.copy()
        "brkitr/word_POSIX.brk" -> BreakWordPosixData.copy()
        "brkitr/burmesedict.dict" -> DictionaryBurmesedictData.copy()
        "brkitr/cjdict.dict" -> DictionaryCjdictData.copy()
        "brkitr/khmerdict.dict" -> DictionaryKhmerdictData.copy()
        "brkitr/laodict.dict" -> DictionaryLaodictData.copy()
        "brkitr/thaidict.dict" -> DictionaryThaidictData.copy()
        "rbnf/af.res" -> RbnfAfResourceData.copy()
        "rbnf/ak.res" -> RbnfAkResourceData.copy()
        "rbnf/am.res" -> RbnfAmResourceData.copy()
        "rbnf/ar.res" -> RbnfArResourceData.copy()
        "rbnf/ar_SA.res" -> RbnfArSAResourceData.copy()
        "rbnf/ars.res" -> RbnfArsResourceData.copy()
        "rbnf/az.res" -> RbnfAzResourceData.copy()
        "rbnf/be.res" -> RbnfBeResourceData.copy()
        "rbnf/bg.res" -> RbnfBgResourceData.copy()
        "rbnf/bs.res" -> RbnfBsResourceData.copy()
        "rbnf/ca.res" -> RbnfCaResourceData.copy()
        "rbnf/ccp.res" -> RbnfCcpResourceData.copy()
        "rbnf/chr.res" -> RbnfChrResourceData.copy()
        "rbnf/cs.res" -> RbnfCsResourceData.copy()
        "rbnf/cy.res" -> RbnfCyResourceData.copy()
        "rbnf/da.res" -> RbnfDaResourceData.copy()
        "rbnf/de.res" -> RbnfDeResourceData.copy()
        "rbnf/de_CH.res" -> RbnfDeCHResourceData.copy()
        "rbnf/ee.res" -> RbnfEeResourceData.copy()
        "rbnf/el.res" -> RbnfElResourceData.copy()
        "rbnf/en.res" -> RbnfEnResourceData.copy()
        "rbnf/en_001.res" -> RbnfEn001ResourceData.copy()
        "rbnf/en_IN.res" -> RbnfEnINResourceData.copy()
        "rbnf/eo.res" -> RbnfEoResourceData.copy()
        "rbnf/es.res" -> RbnfEsResourceData.copy()
        "rbnf/es_419.res" -> RbnfEs419ResourceData.copy()
        "rbnf/es_DO.res" -> RbnfEsDOResourceData.copy()
        "rbnf/es_GT.res" -> RbnfEsGTResourceData.copy()
        "rbnf/es_HN.res" -> RbnfEsHNResourceData.copy()
        "rbnf/es_MX.res" -> RbnfEsMXResourceData.copy()
        "rbnf/es_NI.res" -> RbnfEsNIResourceData.copy()
        "rbnf/es_PA.res" -> RbnfEsPAResourceData.copy()
        "rbnf/es_PR.res" -> RbnfEsPRResourceData.copy()
        "rbnf/es_SV.res" -> RbnfEsSVResourceData.copy()
        "rbnf/es_US.res" -> RbnfEsUSResourceData.copy()
        "rbnf/et.res" -> RbnfEtResourceData.copy()
        "rbnf/fa.res" -> RbnfFaResourceData.copy()
        "rbnf/fa_AF.res" -> RbnfFaAFResourceData.copy()
        "rbnf/ff.res" -> RbnfFfResourceData.copy()
        "rbnf/fi.res" -> RbnfFiResourceData.copy()
        "rbnf/fil.res" -> RbnfFilResourceData.copy()
        "rbnf/fo.res" -> RbnfFoResourceData.copy()
        "rbnf/fr.res" -> RbnfFrResourceData.copy()
        "rbnf/fr_BE.res" -> RbnfFrBEResourceData.copy()
        "rbnf/fr_CH.res" -> RbnfFrCHResourceData.copy()
        "rbnf/ga.res" -> RbnfGaResourceData.copy()
        "rbnf/gu.res" -> RbnfGuResourceData.copy()
        "rbnf/he.res" -> RbnfHeResourceData.copy()
        "rbnf/hi.res" -> RbnfHiResourceData.copy()
        "rbnf/hr.res" -> RbnfHrResourceData.copy()
        "rbnf/hu.res" -> RbnfHuResourceData.copy()
        "rbnf/hy.res" -> RbnfHyResourceData.copy()
        "rbnf/id.res" -> RbnfIdResourceData.copy()
        "rbnf/in.res" -> RbnfInResourceData.copy()
        "rbnf/is.res" -> RbnfIsResourceData.copy()
        "rbnf/it.res" -> RbnfItResourceData.copy()
        "rbnf/iw.res" -> RbnfIwResourceData.copy()
        "rbnf/ja.res" -> RbnfJaResourceData.copy()
        "rbnf/ka.res" -> RbnfKaResourceData.copy()
        "rbnf/kk.res" -> RbnfKkResourceData.copy()
        "rbnf/kl.res" -> RbnfKlResourceData.copy()
        "rbnf/km.res" -> RbnfKmResourceData.copy()
        "rbnf/ko.res" -> RbnfKoResourceData.copy()
        "rbnf/ky.res" -> RbnfKyResourceData.copy()
        "rbnf/lb.res" -> RbnfLbResourceData.copy()
        "rbnf/lo.res" -> RbnfLoResourceData.copy()
        "rbnf/lrc.res" -> RbnfLrcResourceData.copy()
        "rbnf/lt.res" -> RbnfLtResourceData.copy()
        "rbnf/lv.res" -> RbnfLvResourceData.copy()
        "rbnf/mk.res" -> RbnfMkResourceData.copy()
        "rbnf/ms.res" -> RbnfMsResourceData.copy()
        "rbnf/mt.res" -> RbnfMtResourceData.copy()
        "rbnf/my.res" -> RbnfMyResourceData.copy()
        "rbnf/nb.res" -> RbnfNbResourceData.copy()
        "rbnf/ne.res" -> RbnfNeResourceData.copy()
        "rbnf/nl.res" -> RbnfNlResourceData.copy()
        "rbnf/nn.res" -> RbnfNnResourceData.copy()
        "rbnf/no.res" -> RbnfNoResourceData.copy()
        "rbnf/pl.res" -> RbnfPlResourceData.copy()
        "rbnf/pt.res" -> RbnfPtResourceData.copy()
        "rbnf/pt_PT.res" -> RbnfPtPTResourceData.copy()
        "rbnf/qu.res" -> RbnfQuResourceData.copy()
        "rbnf/res_index.res" -> RbnfResIndexResourceData.copy()
        "rbnf/ro.res" -> RbnfRoResourceData.copy()
        "rbnf/root.res" -> RbnfRootResourceData.copy()
        "rbnf/ru.res" -> RbnfRuResourceData.copy()
        "rbnf/se.res" -> RbnfSeResourceData.copy()
        "rbnf/sh.res" -> RbnfShResourceData.copy()
        "rbnf/sk.res" -> RbnfSkResourceData.copy()
        "rbnf/sl.res" -> RbnfSlResourceData.copy()
        "rbnf/sq.res" -> RbnfSqResourceData.copy()
        "rbnf/sr.res" -> RbnfSrResourceData.copy()
        "rbnf/sr_Latn.res" -> RbnfSrLatnResourceData.copy()
        "rbnf/su.res" -> RbnfSuResourceData.copy()
        "rbnf/sv.res" -> RbnfSvResourceData.copy()
        "rbnf/sw.res" -> RbnfSwResourceData.copy()
        "rbnf/ta.res" -> RbnfTaResourceData.copy()
        "rbnf/th.res" -> RbnfThResourceData.copy()
        "rbnf/tr.res" -> RbnfTrResourceData.copy()
        "rbnf/uk.res" -> RbnfUkResourceData.copy()
        "rbnf/vec.res" -> RbnfVecResourceData.copy()
        "rbnf/vi.res" -> RbnfViResourceData.copy()
        "rbnf/yue.res" -> RbnfYueResourceData.copy()
        "rbnf/yue_Hans.res" -> RbnfYueHansResourceData.copy()
        "rbnf/zh.res" -> RbnfZhResourceData.copy()
        "rbnf/zh_HK.res" -> RbnfZhHKResourceData.copy()
        "rbnf/zh_Hant.res" -> RbnfZhHantResourceData.copy()
        "rbnf/zh_Hant_HK.res" -> RbnfZhHantHKResourceData.copy()
        "rbnf/zh_Hant_MO.res" -> RbnfZhHantMOResourceData.copy()
        "rbnf/zh_Hant_TW.res" -> RbnfZhHantTWResourceData.copy()
        "rbnf/zh_MO.res" -> RbnfZhMOResourceData.copy()
        "rbnf/zh_TW.res" -> RbnfZhTWResourceData.copy()
        else -> ZoneResourceRegistry.getBytes(itemPath(resourceName)) ?: RegionResourceRegistry.getBytes(itemPath(resourceName)) ?: CoreResourceRegistry.getBytes(itemPath(resourceName)) ?: UnitResourceRegistry.getBytes(itemPath(resourceName))
    }
}
