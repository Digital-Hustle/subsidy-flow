// Synthetic API fixtures only: validates frontend behavior, not backend integration.
const base = process.env.FRONTEND_URL || 'http://127.0.0.1:5173';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const app = { orgInn:'1234567890', orgName:'Тестовая организация', ogrn:'1234567890123', address:{value:'Тестовый адрес',purpose:'LEGAL'}, project:'Тестовый проект', requestedAmount:'100000.00', totalCost:'200000.00', ownFunds:'100000.00', applicationDate:'2026-10-10', signatory:{fullName:'Тестовый подписант',position:'Директор',authority:'Устав'}, property:null };
const fld=(name,value,raw=null)=>({name,value,raw,confidence:0.92,checks:[],page:1});
const doc = { documentId:'d1',fileId:'f1',fileVersion:1,fromPage:1,toPage:2,type:'GUARANTEE_LETTER',typeConfidence:0.91,stage:'PARSED',reasonCode:null,evidence:null,source:'MODEL',resultVersion:1,fields:[fld('guarantorName','Тестовый гарант'),fld('beneficiaryName','Тестовый заявитель'),fld('signaturePresent',true,'Подпись руководителя'),fld('stampPresent',false),fld('amount','100000.00')],model:'test-model' };
let role='SENIOR_SPECIALIST', failEdit=true, rejectReplace=true, failPreview=false;
let card={ packageId:'p1',status:'COLLECTING',program:'INNOVATION',org:{inn:app.orgInn,name:app.orgName},protocol:null,decision:null,updatedAt:new Date().toISOString(),application:app,applicationRevision:1,files:[{fileId:'f1',version:1,name:'guarantee.pdf',mime:'application/pdf',pages:2,stage:'PARSED',reasonCode:null,documents:[doc],electronicSignature:{status:'UNKNOWN',present:null,reason:'NOT_INSPECTED',cryptographicallyVerified:false}},{fileId:'f2',version:1,name:'bad-scan.png',mime:'image/png',pages:null,stage:'UNREADABLE',reasonCode:'IMAGE_BLURRY',documents:[]}] };
const seen={uploads:[],replacements:0,reprocess:0,pages:[],created:[],edits:[],downloads:[]};
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==','base64');
let browser,page;
(async()=>{
 browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || undefined});
 const context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});
 await context.addInitScript(() => {
  window.__streams=[];
  window.EventSource=class extends EventTarget {
   constructor(url){super();this.url=url;this.closed=false;window.__streams.push(this);queueMicrotask(()=>this.onopen?.());}
   close(){this.closed=true;}
  };
 });
 page=await context.newPage();page.setDefaultTimeout(15000);
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await context.route(/https:\/\/.*/,route=>route.fulfill({status:200,contentType:'text/css',body:''}));
 await page.route('**/*/api/v1/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname,method=req.method();
  const json=(body,status=200)=>route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)});
  if(path.endsWith('/auth/mode')) return json({mode:'demo',demo:[],demoCode:'123456'});
  if(path.endsWith('/auth/refresh'))return json({accessToken:'test-token',expiresAt:Date.now()+3600000,sessionExpiresAt:Date.now()+7200000,user:{id:'u1',name:'Тестовый специалист',email:'synthetic@example.test',role,status:'ACTIVE',demo:true,lastSeen:null}});
  if(path.endsWith('/model/status'))return json({provider:'test',model:'test',visionModel:null,reachable:true,error:null});
  if(path.endsWith('/stream/ticket'))return json({ticket:'synthetic-ticket'});
  if(path.endsWith('/stream'))return route.fulfill({status:200,contentType:'text/event-stream',body:': ping\n\n'});
  if(path.endsWith('/programs'))return json({items:[{code:'INNOVATION',name:'Инновационный проект'},{code:'BEACH',name:'Обустройство пляжа'},{code:'MODERNIZATION',name:'Модернизация производства'}]});
  if(path.endsWith('/doc-types'))return json({items:[{code:'GUARANTEE_LETTER',name:'Гарантийное письмо'}]});
  if(path.endsWith('/documents'))return json({items:[doc]});
  if(path.includes('/pages/')){seen.pages.push(url.search);if(failPreview)return json({code:'PREVIEW_FAILED',detail:'Нет превью'},503);return route.fulfill({status:200,contentType:'image/png',body:png});}
  if(path.endsWith('/content')){seen.downloads.push({path,version:url.searchParams.get('version'),auth:req.headers().authorization});return route.fulfill({status:200,contentType:'application/pdf',headers:{'content-disposition':'attachment; filename="original.pdf"'},body:'%PDF-1.4 synthetic'});}
  if(path.endsWith('/reprocess')){seen.reprocess++;await new Promise(r=>setTimeout(r,150));return json({},202);}
  if(path.endsWith('/versions')){
   seen.replacements++;if(rejectReplace)return json({files:[],rejected:[{name:'replacement.pdf',code:'PDF_ENCRYPTED',detail:'PDF защищён паролем',hint:'Снимите пароль'}]},202);
   card.files[0]={...card.files[0],version:2,stage:'RECEIVED',documents:[],pages:1};return json({files:[{fileId:'f1',stage:'RECEIVED',duplicateOf:null}],rejected:[]},202);
  }
  if(path.endsWith('/files')&&method==='POST'){
   seen.uploads.push(req.postData());return json({files:[{fileId:'u1',stage:'RECEIVED',duplicateOf:null},{fileId:'u2',stage:'RECEIVED',duplicateOf:null}],rejected:[{name:'scan.png',code:'FILE_CORRUPTED',detail:'Повреждённое изображение',hint:'Сохраните изображение заново'}]},202);
  }
  if(path.endsWith('/application')&&method==='PUT'){
   const body=req.postDataJSON();seen.edits.push(body);
   if(failEdit)return json({code:'VALIDATION_ERROR',detail:'Проверьте заявку',hint:'Исправьте поле',errors:[{field:'application.orgName',message:'Уточните наименование'}]},400);
   card={...card,application:body.application,applicationRevision:card.applicationRevision+1};return json({applicationRevision:card.applicationRevision});
  }
  if(path.endsWith('/packages')&&method==='POST'){
   const body=req.postDataJSON();seen.created.push({body,key:req.headers()['idempotency-key']});
   card={...card,application:body.application,program:body.programCode};return json({packageId:'p1',status:'COLLECTING',org:card.org,program:card.program},201);
  }
  if(path.endsWith('/packages'))return json({items:[],page:{next:null,total:0}});
  if(path.endsWith('/packages/p1'))return json(card);
  if(path.endsWith('/protocol'))return json({code:'NOT_CHECKED_YET',detail:'Пакет ещё не проверен'},409);
  if(path.endsWith('/protocols'))return json({items:[]});
  console.log('UNHANDLED',method,path);return json({items:[]});
 });
 const go=async()=>{await page.goto(`${base}/operator/applications/p1`,{waitUntil:'domcontentloaded'});await page.getByRole('heading',{name:card.org.name,exact:true}).waitFor();};
 await go();await page.waitForLoadState('networkidle',{timeout:5000}).catch(()=>{});
 await page.getByRole('tab',{name:'Гарантийное письмо',exact:true}).click();
 await page.getByText('Обнаружено',{exact:true}).waitFor();await page.getByText('Не обнаружено',{exact:true}).waitFor();
 assert.equal(await page.getByText('Тестовый гарант',{exact:true}).count(),2);
 await page.getByRole('region',{name:'Признаки подписей и печати'}).getByText('Не определено',{exact:true}).waitFor();
 await page.screenshot({path:'/tmp/f1-desktop-light.png',fullPage:true,timeout:7000});
 console.log('PASS parties and tri-state indicators');
 await page.getByRole('button',{name:'Редактировать',exact:true}).click();
 const dialog=page.getByRole('dialog',{name:'Редактирование заявки'});await dialog.waitFor();
 await dialog.getByLabel('Наименование организации',{exact:true}).fill('Изменённая организация');
 await dialog.getByRole('button',{name:'Сохранить',exact:true}).click();await dialog.getByText('Уточните наименование',{exact:true}).waitFor();
 assert.equal(await dialog.getByLabel('Наименование организации',{exact:true}).inputValue(),'Изменённая организация');
 failEdit=false;await dialog.getByRole('button',{name:'Сохранить',exact:true}).click();await dialog.waitFor({state:'hidden'});
 assert.equal(seen.edits[0].expectedRevision,1);assert.equal(seen.edits[1].expectedRevision,1);
 console.log('PASS edit error preserves draft and revision');
 await page.getByLabel('Документы для загрузки',{exact:true}).setInputFiles([
 {name:'document.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF')},
 {name:'table.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:Buffer.from('test')},
 {name:'scan.png',mimeType:'image/png',buffer:Buffer.from('image')},
 {name:'archive.zip',mimeType:'application/zip',buffer:Buffer.from('archive')}]);
 await page.getByText('Принято файлов: 2.',{exact:false}).waitFor();await page.getByText('Повреждённое изображение',{exact:false}).waitFor();await page.getByText('archive.zip',{exact:false}).waitFor();
 assert.ok(!seen.uploads[0].includes('filename="archive.zip"'));assert.ok(seen.uploads[0].includes('filename="table.docx"'));
 console.log('PASS case formats, client and server refusals, partial success');
 await page.locator('#upload-documents > div').first().evaluate(el=>{
  const transfer=new DataTransfer();transfer.items.add(new File(['synthetic'],'drop.xlsx',{type:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'}));
  transfer.items.add(new File(['synthetic'],'drop.zip',{type:'application/zip'}));
  el.dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:transfer}));
 });
 await page.getByText('drop.zip',{exact:false}).waitFor();
 await page.waitForFunction(()=>!document.querySelector('input[aria-label="Документы для загрузки"]').disabled);
 assert.ok(seen.uploads.at(-1).includes('filename="drop.xlsx"'));assert.ok(!seen.uploads.at(-1).includes('filename="drop.zip"'));
 console.log('PASS drag and drop mixed formats');
 await page.getByLabel('Документ для замены',{exact:true}).setInputFiles({name:'replacement.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF')});
 await page.getByText('PDF защищён паролем',{exact:false}).waitFor();assert.equal(await page.getByText('Загружена новая версия',{exact:true}).count(),0);
 rejectReplace=false;await page.getByLabel('Документ для замены',{exact:true}).setInputFiles({name:'replacement.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF')});
 await page.getByRole('tab',{name:'guarantee.pdf',exact:true}).waitFor();
 await page.waitForFunction(()=>document.querySelector('img[alt="Страница 1"]')?.complete);
 await page.getByText('Тестовый гарант',{exact:true}).first().waitFor({state:'hidden'});assert.ok(seen.pages.includes('?version=2'));assert.equal(await page.getByText('Тестовый гарант',{exact:true}).count(),0);
 console.log('PASS rejected replacement and versioned preview, no old extraction');
 await page.getByRole('tab',{name:'bad-scan.png',exact:true}).click();
 await page.getByText('Обработка завершилась с проблемой.',{exact:false}).waitFor();
 const downloaded=page.waitForEvent('download');await page.getByRole('button',{name:'Оригинал',exact:true}).click();await downloaded;
 assert.deepEqual(seen.downloads.at(-1),{path:'/package/api/v1/files/f2/content',version:'1',auth:'Bearer test-token'});
 await page.getByRole('button',{name:'Разобрать файл заново',exact:true}).click();await page.getByText('Файл отправлен на повторный разбор',{exact:true}).waitFor();assert.equal(seen.reprocess,1);
 console.log('PASS unreadable file without documents, original and retry');
 card.files[1].stage='WAITING_MODEL';card.files[1].reasonCode=null;
 await page.evaluate(()=>window.__streams.filter(s=>!s.closed).forEach(s=>s.dispatchEvent(new MessageEvent('file.stage',{data:'{}'}))));
 await page.getByText('Модель временно недоступна.',{exact:false}).waitFor();
 card.files[1].stage='FAILED';
 await page.evaluate(()=>window.__streams.filter(s=>!s.closed).forEach(s=>s.dispatchEvent(new MessageEvent('file.stage',{data:'{}'}))));
 await page.getByText('Обработка завершилась с проблемой.',{exact:false}).waitFor();
 failPreview=true;await go();await page.getByRole('tab',{name:'bad-scan.png',exact:true}).click();
 await page.getByText('Страница не открылась',{exact:true}).waitFor();
 const noPreviewDownload=page.waitForEvent('download');await page.getByRole('button',{name:'Оригинал',exact:true}).click();await noPreviewDownload;
 await page.getByRole('button',{name:'Заменить файл',exact:true}).waitFor();
 failPreview=false;await page.getByRole('button',{name:'Повторить',exact:true}).click();await page.locator('img[alt="Страница 1"]').waitFor();
 console.log('PASS SSE stage refresh and preview failure recovery');
 await page.setViewportSize({width:390,height:844});await page.evaluate(()=>document.documentElement.classList.add('dark'));
 await page.screenshot({path:'/tmp/f1-mobile-dark.png',fullPage:true,timeout:7000});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 console.log('PASS mobile dark layout');
 await page.goto(`${base}/operator`,{waitUntil:'domcontentloaded'});await page.getByRole('button',{name:'Новая заявка',exact:true}).click();
 const create=page.getByRole('dialog',{name:'Новая заявка'});await create.waitFor();
 await create.getByLabel('ИНН организации',{exact:true}).fill('1234567890');
 await create.getByLabel('Программа субсидии',{exact:true}).focus();await page.keyboard.press('ArrowDown');await page.keyboard.press('End');await page.keyboard.press('ArrowUp');await page.keyboard.press('Enter');
 await create.getByLabel('Кадастровый номер',{exact:true}).fill('61:18:1234567:45');await create.getByLabel('Правообладатель',{exact:true}).fill('Тестовый правообладатель');
 await create.getByLabel('Программа субсидии',{exact:true}).click();await page.getByRole('option',{name:'Инновационный проект',exact:true}).click();assert.equal(await create.getByLabel('Кадастровый номер',{exact:true}).count(),0);
 await create.getByLabel('Программа субсидии',{exact:true}).click();await page.getByRole('option',{name:'Модернизация производства',exact:true}).click();assert.equal(await create.getByLabel('Кадастровый номер',{exact:true}).inputValue(),'61:18:1234567:45');
 await create.getByLabel('Дата заявки',{exact:true}).click();await page.getByRole('button',{name:'Сегодня',exact:true}).click();await page.getByRole('button',{name:'Сегодня',exact:true}).waitFor({state:'hidden'});await page.getByRole('option',{name:'Модернизация производства',exact:true}).waitFor({state:'hidden'});
 await page.screenshot({path:'/tmp/f1-form-mobile.png',fullPage:true,timeout:7000});assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 await create.getByLabel('Программа субсидии',{exact:true}).click();await page.getByRole('option',{name:'Инновационный проект',exact:true}).click();
 await create.getByRole('button',{name:'Создать',exact:true}).click();await create.waitFor({state:'hidden'});assert.equal(seen.created[0].body.application.property,null);assert.ok(seen.created[0].key);
 console.log('PASS create and property switching, date picker, mobile form');
 card.status='DECIDED';await go();assert.equal(await page.getByRole('button',{name:'Редактировать',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Заменить файл',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'Разобрать файл заново',exact:true}).count(),0);await page.getByRole('button',{name:'Оригинал',exact:true}).waitFor();
 role='AUDITOR';await go();assert.equal(await page.getByRole('button',{name:'Оригинал',exact:true}).count(),0);assert.equal(await page.getByLabel('Документы для загрузки',{exact:true}).count(),0);
 card.status='COLLECTING';role='SPECIALIST';await go();await page.getByRole('button',{name:'Заменить файл',exact:true}).waitFor();assert.equal(await page.getByRole('button',{name:'Разобрать файл заново',exact:true}).count(),0);
 console.log('PASS roles and decided state');assert.deepEqual(errors,[]);console.log('PASS no browser runtime errors');await browser.close();
})().catch(async e=>{console.error(e);if(page){await page.screenshot({path:'/tmp/f1-failure.png',fullPage:true,timeout:3000}).catch(()=>{});fs.writeFileSync('/tmp/f1-failure.txt',await page.locator('body').innerText().catch(()=>''));}if(browser)await browser.close();process.exitCode=1;});
