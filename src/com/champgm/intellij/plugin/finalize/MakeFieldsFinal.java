package com.champgm.intellij.plugin.finalize;

import com.champgm.intellij.plugin.Maction;
import com.champgm.intellij.plugin.PluginUtil;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.compiled.ClsModifierListImpl;

public class MakeFieldsFinal extends Maction {
    /**
     * This hierarchy is a little complex. We start with a field, a variable defined at the class level. We give
     * that to a util, ReferencesSearch, which finds all references to that field. We then iterate through those
     * references and get the element that uses a specific reference to resolve to the original field. Once we
     * have that element, we get that element's parent in order to inspect the context in which the element,
     * which uses the reference to resolve the field, is used. In this case, we're checking to make sure that
     * this element isn't being used to assign a new value to the original field.
     * <p/>
     * Thanks again to Dmitry Jemerov for help with the plugin https://devnet.jetbrains.com/message/5532238
     */
    @Override
    protected void doAction(final AnActionEvent actionEvent) {
        final PsiClass psiClass = PluginUtil.getPsiClassFromContext(actionEvent);
        final ImmutableSet<PsiAssignmentExpression> constructorAssignmentExpressions = PluginUtil.getConstructorAssignmentExpressions(psiClass);
        final PsiField[] fields = psiClass.getAllFields();
        // Our fields
        final ImmutableSet.Builder<PsiField> unmodifiedFields = ImmutableSet.builder();
        for (final PsiField field : fields) {
            final PsiModifierList modifierList = field.getModifierList();

            // This indicates that the field we are looking at is not modifiable
            if (modifierList instanceof ClsModifierListImpl) {
                break;
            }

            // Err, found a bug where this does weird stuff in Enum classes.
            // There are probably more of these exception cases out there.
            if (field instanceof PsiEnumConstant) {
                break;
            }

            // This code checks to make sure the @Mock annotation isn't on a field, which really instantiates a
            // field with a mocked version of the class even though you can't really tell from here.
            if (modifierList != null) {
                if (modifierList.findAnnotation("org.mockito.Mock") != null) {
                    break;
                }
            }

            // Now check if it's actually modified
            if (!PluginUtil.isModified(field, constructorAssignmentExpressions)) {
                unmodifiedFields.add(field);
            }
        }

        for (final PsiField field : unmodifiedFields.build()) {
            final PsiModifierList modifierList = field.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty("final", true);
            }
        }
    }
}
