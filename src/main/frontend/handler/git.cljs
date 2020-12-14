(ns frontend.handler.git
  (:require [frontend.util :as util :refer-macros [profile]]
            [promesa.core :as p]
            [frontend.state :as state]

            [frontend.git :as git]
            [frontend.date :as date]
            [goog.object :as gobj]
            [frontend.handler.notification :as notification]
            [frontend.handler.route :as route-handler]
            [frontend.handler.common :as common-handler]
            [frontend.config :as config]
            [cljs-time.local :as tl]
            [frontend.helper :as helper]
            [frontend.db.simple :as db-simple]
            [frontend.handler.utils :as h-utils]))

(defn- set-git-status!
  [repo-url value]
  (h-utils/set-key-value repo-url :git/status value)
  (state/set-git-status! repo-url value))

(defn- set-git-last-pulled-at!
  [repo-url]
  (h-utils/set-key-value repo-url :git/last-pulled-at
                    (date/get-date-time-string (tl/local-now))))

(defn- set-git-error!
  [repo-url value]
  (h-utils/set-key-value repo-url :git/error (if value (str value))))

(defn git-add
  ([repo-url file]
   (git-add repo-url file true))
  ([repo-url file update-status?]
   (when-not (config/local-db? repo-url)
     (-> (p/let [result (git/add repo-url file)]
           (when update-status?
             (common-handler/check-changed-files-status)))
         (p/catch (fn [error]
                    (println "git add '" file "' failed: " error)
                    (js/console.error error)))))))

(defn commit-and-force-push!
  [commit-message pushing?]
  (when-let [repo (frontend.state/get-current-repo)]
    (p/let [remote-oid (common-handler/get-remote-ref repo)
            commit-oid (git/commit repo commit-message (array remote-oid))
            result (git/write-ref! repo commit-oid)
            token (helper/get-github-token repo)
            push-result (git/push repo token true)]
      (reset! pushing? false)
      (notification/clear! nil)
      (route-handler/redirect! {:to :home}))))

(defn git-set-username-email!
  [repo-url {:keys [name email]}]
  (when (and name email)
    (git/set-username-email
     (util/get-repo-dir repo-url)
     name
     email)))
