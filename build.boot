(task-options!
 pom {:url "https://github.com/pleasetrythisathome/agency"
      :scm {:url "https://github.com/pleasetrythisathome/agency"}})

(set-env!
 :source-paths #{}
 :dependencies (->> '[[adzerk/bootlaces "0.1.9"]
                      [adzerk/boot-cljs "0.0-2727-0"]
                      [adzerk/boot-cljs-repl "0.1.8"]
                      [deraen/boot-cljx "0.2.1"]
                      [jeluard/boot-notify "0.1.1"]]
                    (mapv #(conj % :scope "test"))))

(task-options!
 pom {:license {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}
      :url "https://github.com/https://github.com/IB5k/agency"
      :scm {:url "https://github.com/IB5k/agency"}}
 cljs {:source-map true})

(require
 '[adzerk.bootlaces :refer :all])
