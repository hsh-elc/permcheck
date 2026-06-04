package de.hsh.permcheck.internal;

public abstract class ActionInsert extends Insert {

        private Action[] actions;
        private AbstractPermitCheck check;

        protected ActionInsert(AbstractPermitCheck check, Action ... actions) {
            this.check = check;
            this.actions = actions;
        }
        protected abstract String getName(Hook hook);
        
        @Override
        public void onEnterImpl(Hook hook) {
            for (Action a : actions) {
                check.checkAction(getName(hook), a, hook);
            }
        }

}
