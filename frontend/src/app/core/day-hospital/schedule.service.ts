import { Injectable, inject } from '@angular/core';
import { ApiClientService } from '../api/api-client.service';

export interface DayHospitalSettings { chairCount:number; slotMinutes:number; startTime:string; endTime:string; }
export interface InfusionAppointment { id:string; patientId:string; treatmentId:string; cycleNumber:number; applicationDay:number; scheduledAt:string; chair:string; durationMinutes:number; expectedVersion?:number; revision?:number; patientName?:string; patientDni?:string; scheme?:string; diagnosis?:string; clinicalStatus?:string; appointmentConfirmed?:boolean; [key:string]:unknown; }
export interface InfusionCandidate { id?:string; patientId:string; treatmentId:string; cycleNumber:number; applicationDay:number; patientName?:string; patientDni?:string; scheme?:string; drugScheme?:string; durationMinutes:number; suggestedDate?:string; prescriptionConfirmed?:boolean; medicationReady?:boolean; medicationSource?:string; blockedReason?:string; [key:string]:unknown; }

@Injectable({providedIn:'root'}) export class ScheduleService {
 private readonly api=inject(ApiClientService);
 settings(){return this.api.get<{items?:Array<{definition?:Partial<DayHospitalSettings>}>}>('/api/clinical/configuration/day-hospital-settings');}
 appointments(date:string){return this.api.get<{infusions?:InfusionAppointment[]}>('/api/clinical/infusions',{date});}
 candidates(query=''){return this.api.get<{candidates?:InfusionCandidate[]}>('/api/clinical/infusion-candidates',{q:query,includeScheduled:false,onlySchedulingEligible:false});}
 create(candidate:InfusionCandidate,scheduledAt:string,chair:string){return this.api.post<{infusion:InfusionAppointment}>('/api/clinical/infusions',{patientId:Number(candidate.patientId),treatmentId:candidate.treatmentId,cycleNumber:candidate.cycleNumber,applicationDay:candidate.applicationDay,scheduledAt,chair,durationMinutes:candidate.durationMinutes,clinicalStatus:'planned',pharmacyStatus:'pending',administrationStatus:'not_started',appointmentConfirmed:false});}
 move(item:InfusionAppointment,scheduledAt:string,chair:string){return this.api.patch<{infusion:InfusionAppointment}>(`/api/clinical/infusions/${encodeURIComponent(item.id)}`,{expectedVersion:Number(item.expectedVersion??item.revision??0),scheduledAt,chair,durationMinutes:item.durationMinutes});}
 cancel(item:InfusionAppointment,reason='Turno retirado de la agenda'){return this.api.patch<{infusion:InfusionAppointment}>(`/api/clinical/infusions/${encodeURIComponent(item.id)}`,{expectedVersion:Number(item.expectedVersion??item.revision??0),clinicalStatus:'cancelled',pharmacyStatus:'cancelled',administrationStatus:'cancelled',reason});}
}
