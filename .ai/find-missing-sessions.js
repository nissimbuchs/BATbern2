const sessions = require('../apps/BATspa-old/src/api/sessions.json');
const missing = [];

sessions.forEach((s, idx) => {
  if (s.title === 'Programmheft') return;

  const hasAbstract = s.abstract && s.abstract.trim() !== '';
  const hasSpeakers = s.referenten && s.referenten.length > 0;

  if (!hasAbstract || !hasSpeakers) {
    missing.push({
      index: idx,
      bat: s.bat,
      title: s.title,
      pdf: s.pdf,
      missingAbstract: !hasAbstract,
      missingSpeakers: !hasSpeakers
    });
  }
});

console.log('Sessions with missing data:');
missing.forEach(m => {
  console.log(`BAT ${m.bat}: ${m.title}`);
  console.log(`  PDF: ${m.pdf}`);
  console.log(`  Missing: ${m.missingAbstract ? 'Abstract ' : ''}${m.missingSpeakers ? 'Speakers' : ''}`);
  console.log('');
});
console.log(`Total: ${missing.length} sessions with missing data`);

// Output JSON for further processing
console.log('\nJSON output:');
console.log(JSON.stringify(missing, null, 2));
