import { useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { FileCheck2, FileUp, Timer } from "lucide-react";
import { StartForm, ResumeForm } from "@/features/start-application";
import { AutoHeight, Segmented } from "@/shared/ui";
import { fadeUp, spring, stagger, tween } from "@/shared/config/motion";

const STEPS = [
  { icon: FileCheck2, text: "Выберите программу и заполните анкету — покажем, какие документы нужны именно вам" },
  { icon: FileUp, text: "Загрузите документы в PDF — вид документа и реквизиты определим сами" },
  { icon: Timer, text: "Ошибку в документе увидите сразу, а не через неделю — с подсказкой, что исправить" },
];

/** /apply — начало: новая заявка или возврат по коду. */
export const ApplyStartPage = () => {
  const [tab, setTab] = useState<"new" | "resume">("new");
  return (
    <>
      <motion.div variants={stagger(0.07)} initial="hidden" animate="show" className="flex flex-col items-center text-center">
        <motion.p variants={fadeUp} className="text-sm font-medium text-brand">Агентство инноваций</motion.p>
        <motion.h1 variants={fadeUp} className="mt-4 text-balance font-display text-4xl font-semibold leading-none tracking-hero sm:text-5xl">Заявка на субсидию</motion.h1>
        <motion.p variants={fadeUp} className="mt-3 max-w-md text-pretty text-base text-foreground/70">Соберите пакет документов онлайн — без регистрации и пароля, вернуться можно по ссылке или коду</motion.p>
      </motion.div>
      <motion.section variants={fadeUp} initial="hidden" animate="show" className="mt-6 overflow-hidden rounded-3xl bg-card/85 p-5 shadow-card backdrop-blur-xl sm:p-8">
        <Segmented block size="lg" className="mb-5" label="Заявка" value={tab} onChange={setTab}
          options={[{ value: "new", label: "Новая заявка" }, { value: "resume", label: "У меня есть код" }]} />
        <AutoHeight>
          <AnimatePresence mode="wait" initial={false}>
            <motion.div key={tab} initial={{ opacity: 0, x: tab === "new" ? -24 : 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, transition: tween.exit }} transition={{ ...spring.soft, opacity: tween.base }}>
              {tab === "new" ? <StartForm /> : <ResumeForm />}
            </motion.div>
          </AnimatePresence>
        </AutoHeight>
      </motion.section>
      <motion.ul variants={stagger(0.06, 0.15)} initial="hidden" animate="show" className="mt-6 grid gap-2 sm:grid-cols-3">
        {STEPS.map(({ icon: Icon, text }, i) => (
          <motion.li key={text} variants={fadeUp} className="flex items-start gap-3 rounded-xl bg-card/70 p-4 text-sm shadow-card backdrop-blur-xl sm:flex-col">
            <span className="relative grid size-10 shrink-0 place-items-center overflow-hidden rounded-md bg-brand-deep text-white"><span aria-hidden className="absolute inset-0 bg-sheen" /><Icon className="relative size-5" /></span>
            <span className="min-w-0"><span className="block text-xs font-medium text-muted-foreground">Шаг {i + 1}</span><span className="text-pretty">{text}</span></span>
          </motion.li>
        ))}
      </motion.ul>
    </>
  );
};
