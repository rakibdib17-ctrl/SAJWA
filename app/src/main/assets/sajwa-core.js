/* SAJWA Core State v1 — single serialized entry point for language and appearance.
 * Legacy feature renderers remain the feature adapters; all user-triggered changes pass
 * through this coordinator so rapid taps cannot run overlapping render transactions.
 */
(function (w, d) {
  'use strict';
  if (w.SajwaCore) return;
  const KEY_LANG='sajwa-lang', KEY_THEME='sajwa-theme', KEY_DARK='sajwa-dark';
  const LANGS=new Set(['en','bn','ar']);
  const THEMES=new Set(['emerald','ocean','midnight','royal','rose','sand','pink']);
  let sequence=0, queue=Promise.resolve(), applying=false;
  const read=(key,fallback)=>{try{return localStorage.getItem(key)||fallback}catch(_){return fallback}};
  const state=()=>({
    language: LANGS.has(read(KEY_LANG,'en'))?read(KEY_LANG,'en'):'en',
    theme: THEMES.has(read(KEY_THEME,'emerald'))?read(KEY_THEME,'emerald'):'emerald',
    dark: read(KEY_DARK,'0')==='1',
    direction: d.documentElement.dir==='rtl'?'rtl':'ltr',
    revision: sequence
  });
  function enqueue(kind, value, fn) {
    const revision=++sequence;
    queue=queue.catch(()=>{}).then(()=>new Promise(resolve=>{
      applying=true;
      try {
        fn(value);
        d.documentElement.dataset.sajwaRevision=String(revision);
        d.dispatchEvent(new CustomEvent('sajwa:state-committed',{detail:{kind,value,revision,state:state()}}));
      } catch(error) {
        w.__sajwaLastError='['+kind+'] '+(error?.stack||error?.message||String(error));
        d.dispatchEvent(new CustomEvent('sajwa:state-error',{detail:{kind,value,revision,error:String(error)}}));
      } finally { applying=false; resolve(); }
    }));
    return queue;
  }
  const api={
    getState:state,
    get revision(){return sequence},
    get applying(){return applying},
    setLanguage(value){
      const lang=LANGS.has(value)?value:'en';
      if(typeof w.setLang!=='function') return Promise.reject(new Error('Language adapter unavailable'));
      return enqueue('language',lang,v=>w.setLang(v));
    },
    setTheme(value){
      const theme=THEMES.has(value)?value:'emerald';
      if(typeof w.applySajwaTheme!=='function') return Promise.reject(new Error('Theme adapter unavailable'));
      return enqueue('theme',theme,v=>w.applySajwaTheme(v));
    },
    setDark(value){
      if(typeof w.setTheme!=='function') return Promise.reject(new Error('Appearance adapter unavailable'));
      return enqueue('dark',!!value,v=>w.setTheme(v));
    },
    whenIdle(){return queue;}
  };
  w.SajwaCore=Object.freeze(api);
  // Intercept the legacy public entry points once. Calls from old inline handlers and
  // settings controls now share one serialized transaction queue.
  for(const name of ['setLang','applySajwaTheme','setTheme']){
    const original=w[name];
    if(typeof original!=='function') continue;
    const kind=name==='setLang'?'language':name==='setTheme'?'dark':'theme';
    const wrapped=function(value){
      const normalized=kind==='language'?(LANGS.has(value)?value:'en'):kind==='theme'?(THEMES.has(value)?value:'emerald'):!!value;
      enqueue(kind,normalized,v=>original.call(this,v));
      return undefined;
    };
    wrapped.__sajwaCoreWrapped=true;
    w[name]=wrapped;
  }
  d.addEventListener('visibilitychange',()=>{if(!d.hidden)d.dispatchEvent(new CustomEvent('sajwa:resume',{detail:state()}))});
  d.dispatchEvent(new CustomEvent('sajwa:core-ready',{detail:state()}));
})(window,document);
