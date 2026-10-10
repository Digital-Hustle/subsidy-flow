# Генератор схем payload событий: python3 gen.py (поля сверены с outbox.append в сервисах).
import json, os
ROOT=os.path.dirname(os.path.abspath(__file__))
U={"type":"string","format":"uuid"}
I={"type":"integer","minimum":1}
S={"type":"string"}
NS={"type":["string","null"]}
STAGES=["QUEUED","EXTRACTING_TEXT","CLASSIFYING","EXTRACTING_FIELDS","VALIDATING","PARSED","NEEDS_REVIEW","UNREADABLE","WAITING_MODEL","FAILED","REMOVED"]
FILE={"fileId":U,"packageId":U,"version":I}
E={
 "package.package.v1":{
  "PackageCreated":({"packageId":U,"orgId":U,"inn":{"type":"string","pattern":"^(\\d{10}|\\d{12})$"},"programCode":S,"createdBy":S},None,"package: пакет создан (POST /packages)"),
  "PackageDecided":({"packageId":U,"orgId":U,"inn":{"type":"string","pattern":"^(\\d{10}|\\d{12})$"},"programCode":S,"decision":{"enum":["APPROVE","REJECT","RETURN"]},"decidedBy":S},None,"package: решение по пакету (FR-46)"),
 },
 "package.file.v1":{
  "FileReceived":({**FILE,"mime":S,"pages":{"type":["integer","null"]}},None,"package: файл принят в пакет"),
  "FileReplaced":({**FILE,"previousVersion":I,"mime":S,"pages":{"type":["integer","null"]}},None,"package: новая версия файла (FR-65)"),
  "FileRemoved":({"fileId":U,"packageId":U},None,"package: файл удалён из пакета (мягко)"),
 },
 "docproc.document.v1":{
  "StageChanged":({**FILE,"stage":{"enum":STAGES},"reason":NS,"reasonCode":NS},["reason","reasonCode"],"docproc: смена стадии файла"),
  "DocumentsSegmented":({**FILE,"documents":{"type":"array","items":{"type":"object","required":["documentId","fromPage","toPage"],"properties":{"documentId":U,"fromPage":I,"toPage":I},"additionalProperties":False}}},None,"docproc: файл разбит на документы (сегменты страниц)"),
  "DocumentParsed":({**FILE,"documentId":U,"fromPage":I,"toPage":I,"type":S,"typeConfidence":{"type":"number","minimum":0,"maximum":1},"source":{"enum":["MODEL","SPECIALIST"]},"stage":{"enum":["PARSED","NEEDS_REVIEW"]},"reason":NS,"reasonCode":NS,"resultVersion":I,
     "fields":{"type":"array","items":{"type":"object","required":["name","value","confidence","checks"],"properties":{"name":S,"value":{},"confidence":{"type":["number","null"]},"checks":{"type":"array","items":{"type":"object","required":["code","status","message"],"properties":{"code":S,"status":S,"message":S}}}}}}},["reason","reasonCode"],"docproc: документ разобран (вид, поля, проверки); новая версия результата — новое событие"),
  "DocumentUnreadable":({**FILE,"reason":S},None,"docproc: файл не прочитать (TEXT_NOT_FOUND и др.)"),
  "DocumentFailed":({**FILE,"reason":S},None,"docproc: разбор не удался после всех попыток"),
 },
 "rules.package.v1":{
  "PackageChecked":({"packageId":U,"protocolId":U,"version":I,"protocolStatus":{"enum":["COMPLETE","INCOMPLETE","NEEDS_REVIEW"]},"completeness":{"type":"integer","minimum":0,"maximum":100},"failed":{"type":"integer","minimum":0},"warnings":{"type":"integer","minimum":0},"needsReview":{"type":"integer","minimum":0}},None,"rules: новая версия протокола проверки"),
 },
}
for topic,types in E.items():
  os.makedirs(f'{ROOT}/{topic}',exist_ok=True)
  for t,(props,optional,desc) in types.items():
    req=[k for k in props if k not in (optional or [])]
    sch={"$schema":"https://json-schema.org/draft/2020-12/schema","$id":f"urn:subsidy:events:{topic}:{t}","title":t,"description":desc+". Это схема payload; конверт — envelope.schema.json.","type":"object","required":req,"properties":props,"additionalProperties":False}
    open(f'{ROOT}/{topic}/{t}.schema.json','w').write(json.dumps(sch,ensure_ascii=False,indent=2)+"\n")
env={"$schema":"https://json-schema.org/draft/2020-12/schema","$id":"urn:subsidy:events:envelope","title":"EventEnvelope","description":"Конверт любого события (libs/common EventEnvelope). Ключ Kafka — aggregateId.","type":"object","required":["eventId","type","occurredAt","aggregateId","payload"],"properties":{"eventId":U,"type":S,"occurredAt":{"type":"string","format":"date-time"},"aggregateId":S,"traceparent":NS,"payload":{"type":"object"}},"additionalProperties":False}
open(f'{ROOT}/envelope.schema.json','w').write(json.dumps(env,ensure_ascii=False,indent=2)+"\n")
