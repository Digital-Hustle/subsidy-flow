import { useQuery } from "@tanstack/react-query";
import { protocolApi, protocolKeys } from "../api/protocolApi";

/** Код программы → название из справочника rules (нет справочника — показываем код). */
export const useProgramName = () => {
  const q = useQuery({ queryKey: protocolKeys.programs, queryFn: protocolApi.programs, staleTime: 5 * 60_000 });
  return (code?: string | null) => (code ? q.data?.find((p) => p.code === code)?.name ?? code : "");
};
