package org.ansj.recognition.impl;

import org.ansj.domain.Nature;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.library.DicLibrary;
import org.ansj.recognition.Recognition;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.tire.domain.SmartForest;

/**
 * 用户自定义词典的词性优先
 * 
 * @author ansj
 *
 */
public class UserDicNatureRecognition implements Recognition {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private Forest[] forests = null;

    public UserDicNatureRecognition() {
        forests = new Forest[] {DicLibrary.get()};
    }

    /**
     * 传入多本词典，后面的会覆盖前面的结果
     * 
     * @param forests
     */
    public UserDicNatureRecognition(Forest... forests) {
        this.forests = forests;
    }

    @Override
    public void recognition(Result result) {
        for (Term term : result) {
            for (int i = forests.length - 1; i > -1; i--) {
                String[] params = getParams(forests[i], term.getName());
                if (params != null) {
                    term.setNature(new Nature(params[0]));
                    break;
                }
            }
        }
    }

    public static String[] getParams(Forest forest, String word) {
        SmartForest<String[]> temp = forest;
        for (int i = 0; i < word.length(); i++) {
            temp = temp.get(word.charAt(i));
            if (temp == null) {
                return null;
            }
        }
        if (temp.getStatus() > 1) {
            return temp.getParam();
        } else {
            return null;
        }
    }
}
